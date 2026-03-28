package io.obtrace.sdk.http;

import io.obtrace.sdk.core.ObtraceClient;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public class InstrumentedHttpClient extends HttpClient implements AutoCloseable {
  private final Tracer tracer;
  private final HttpClient delegate;
  private final ExecutorService ownedExecutor;

  public InstrumentedHttpClient(ObtraceClient obtraceClient) {
    this.tracer = obtraceClient.getTracer();
    this.ownedExecutor = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "obtrace-instrumented-http");
      t.setDaemon(true);
      return t;
    });
    this.delegate = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .executor(ownedExecutor)
        .build();
  }

  public InstrumentedHttpClient(ObtraceClient obtraceClient, HttpClient delegate) {
    this.tracer = obtraceClient.getTracer();
    this.delegate = delegate;
    this.ownedExecutor = null;
  }

  @Override
  public Optional<CookieHandler> cookieHandler() {
    return delegate.cookieHandler();
  }

  @Override
  public Optional<Duration> connectTimeout() {
    return delegate.connectTimeout();
  }

  @Override
  public Redirect followRedirects() {
    return delegate.followRedirects();
  }

  @Override
  public Optional<ProxySelector> proxy() {
    return delegate.proxy();
  }

  @Override
  public SSLContext sslContext() {
    return delegate.sslContext();
  }

  @Override
  public SSLParameters sslParameters() {
    return delegate.sslParameters();
  }

  @Override
  public Optional<Authenticator> authenticator() {
    return delegate.authenticator();
  }

  @Override
  public Version version() {
    return delegate.version();
  }

  @Override
  public Optional<Executor> executor() {
    return delegate.executor();
  }

  @Override
  public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
      throws IOException, InterruptedException {
    String method = request.method();
    String url = request.uri().toString();

    Span span = tracer.spanBuilder("HTTP " + method)
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute("http.method", method)
        .setAttribute("http.url", url)
        .startSpan();

    try (Scope ignored = span.makeCurrent()) {
      HttpResponse<T> res = delegate.send(request, responseBodyHandler);
      span.setAttribute("http.status_code", (long) res.statusCode());
      if (res.statusCode() >= 400) {
        span.setStatus(StatusCode.ERROR, "HTTP " + res.statusCode());
      } else {
        span.setStatus(StatusCode.OK);
      }
      return res;
    } catch (IOException | InterruptedException ex) {
      span.recordException(ex);
      span.setStatus(StatusCode.ERROR, ex.getMessage() != null ? ex.getMessage() : "error");
      throw ex;
    } finally {
      span.end();
    }
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
    return sendAsync(request, responseBodyHandler, null);
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
    String method = request.method();
    String url = request.uri().toString();

    Span span = tracer.spanBuilder("HTTP " + method)
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute("http.method", method)
        .setAttribute("http.url", url)
        .startSpan();

    CompletableFuture<HttpResponse<T>> future = pushPromiseHandler != null
        ? delegate.sendAsync(request, responseBodyHandler, pushPromiseHandler)
        : delegate.sendAsync(request, responseBodyHandler);

    return future.whenComplete((res, ex) -> {
      if (ex != null) {
        span.recordException(ex);
        span.setStatus(StatusCode.ERROR, ex.getMessage() != null ? ex.getMessage() : "error");
      } else {
        span.setAttribute("http.status_code", (long) res.statusCode());
        if (res.statusCode() >= 400) {
          span.setStatus(StatusCode.ERROR, "HTTP " + res.statusCode());
        } else {
          span.setStatus(StatusCode.OK);
        }
      }
      span.end();
    });
  }

  @Override
  public void close() {
    if (ownedExecutor != null) {
      ownedExecutor.shutdown();
      try {
        if (!ownedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          ownedExecutor.shutdownNow();
        }
      } catch (InterruptedException e) {
        ownedExecutor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }
}
