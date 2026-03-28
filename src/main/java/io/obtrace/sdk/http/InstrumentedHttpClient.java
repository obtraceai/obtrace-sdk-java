package io.obtrace.sdk.http;

import io.obtrace.sdk.core.ObtraceClient;
import io.obtrace.sdk.model.ObtraceContext;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

public class InstrumentedHttpClient extends HttpClient implements AutoCloseable {
  private final ObtraceClient obtraceClient;
  private final HttpClient delegate;
  private final ExecutorService ownedExecutor;

  public InstrumentedHttpClient(ObtraceClient obtraceClient) {
    this.obtraceClient = obtraceClient;
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
    this.obtraceClient = obtraceClient;
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
    String[] trace = obtraceClient.span("http.client " + method, null, null, null, "", java.util.Map.of("http.method", method, "http.url", url));
    Instant started = Instant.now();

    HttpRequest instrumented = injectHeaders(request, trace[0], trace[1]);

    try {
      HttpResponse<T> res = delegate.send(instrumented, responseBodyHandler);
      long durMs = Duration.between(started, Instant.now()).toMillis();
      ObtraceContext ctx = new ObtraceContext();
      ctx.traceId = trace[0];
      ctx.spanId = trace[1];
      ctx.method = method;
      ctx.endpoint = url;
      ctx.statusCode = res.statusCode();
      ctx.attrs.put("duration_ms", durMs);
      obtraceClient.log("info", "java http request complete", ctx);
      return res;
    } catch (IOException | InterruptedException ex) {
      long durMs = Duration.between(started, Instant.now()).toMillis();
      ObtraceContext ctx = new ObtraceContext();
      ctx.traceId = trace[0];
      ctx.spanId = trace[1];
      ctx.method = method;
      ctx.endpoint = url;
      ctx.attrs.put("duration_ms", durMs);
      obtraceClient.log("error", "java http request failed: " + ex.getMessage(), ctx);
      throw ex;
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
    String[] trace = obtraceClient.span("http.client " + method, null, null, null, "", java.util.Map.of("http.method", method, "http.url", url));
    Instant started = Instant.now();

    HttpRequest instrumented = injectHeaders(request, trace[0], trace[1]);

    CompletableFuture<HttpResponse<T>> future = pushPromiseHandler != null
        ? delegate.sendAsync(instrumented, responseBodyHandler, pushPromiseHandler)
        : delegate.sendAsync(instrumented, responseBodyHandler);

    return future.whenComplete((res, ex) -> {
      long durMs = Duration.between(started, Instant.now()).toMillis();
      ObtraceContext ctx = new ObtraceContext();
      ctx.traceId = trace[0];
      ctx.spanId = trace[1];
      ctx.method = method;
      ctx.endpoint = url;
      ctx.attrs.put("duration_ms", durMs);
      if (ex != null) {
        obtraceClient.log("error", "java http request failed: " + ex.getMessage(), ctx);
      } else {
        ctx.statusCode = res.statusCode();
        obtraceClient.log("info", "java http request complete", ctx);
      }
    });
  }

  private HttpRequest injectHeaders(HttpRequest original, String traceId, String spanId) {
    java.util.Map<String, String> propagation = obtraceClient.injectPropagation(
        new java.util.HashMap<>(), traceId, spanId, null);

    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(original.uri())
        .method(original.method(), original.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
    original.timeout().ifPresent(builder::timeout);

    original.headers().map().forEach((name, values) -> {
      for (String v : values) {
        builder.header(name, v);
      }
    });

    propagation.forEach((name, value) -> {
      if (original.headers().firstValue(name).isEmpty()) {
        builder.header(name, value);
      }
    });

    return builder.build();
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
