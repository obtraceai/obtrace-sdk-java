package io.obtrace.sdk.http;

import io.obtrace.sdk.core.ObtraceClient;
import io.obtrace.sdk.model.ObtraceContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class InstrumentedHttpClient {
  private final ObtraceClient client;
  private final HttpClient http;

  public InstrumentedHttpClient(ObtraceClient client) {
    this.client = client;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public HttpResponse<String> send(String method, String url, String body, Map<String, String> headers) throws IOException, InterruptedException {
    String[] trace = client.span("http.client " + method.toUpperCase(), null, null, null, "", Map.of("http.method", method.toUpperCase(), "http.url", url));
    Instant started = Instant.now();

    Map<String, String> h = client.injectPropagation(headers == null ? new HashMap<>() : headers, trace[0], trace[1], null);

    HttpRequest.Builder rb = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(10));

    for (Map.Entry<String, String> e : h.entrySet()) {
      rb.header(e.getKey(), e.getValue());
    }

    String m = method.toUpperCase();
    HttpRequest req = switch (m) {
      case "POST", "PUT", "PATCH" -> rb.method(m, HttpRequest.BodyPublishers.ofString(body == null ? "" : body)).build();
      case "DELETE" -> rb.DELETE().build();
      default -> rb.GET().build();
    };

    try {
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      long durMs = Duration.between(started, Instant.now()).toMillis();
      ObtraceContext ctx = new ObtraceContext();
      ctx.traceId = trace[0];
      ctx.spanId = trace[1];
      ctx.method = m;
      ctx.endpoint = url;
      ctx.statusCode = res.statusCode();
      ctx.attrs.put("duration_ms", durMs);
      client.log("info", "java http request complete", ctx);
      return res;
    } catch (IOException | InterruptedException ex) {
      long durMs = Duration.between(started, Instant.now()).toMillis();
      ObtraceContext ctx = new ObtraceContext();
      ctx.traceId = trace[0];
      ctx.spanId = trace[1];
      ctx.method = m;
      ctx.endpoint = url;
      ctx.attrs.put("duration_ms", durMs);
      client.log("error", "java http request failed: " + ex.getMessage(), ctx);
      throw ex;
    }
  }
}
