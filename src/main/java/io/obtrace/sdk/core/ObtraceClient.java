package io.obtrace.sdk.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.obtrace.sdk.model.ObtraceConfig;
import io.obtrace.sdk.model.ObtraceContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ObtraceClient {
  private final ObtraceConfig cfg;
  private final HttpClient http;
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<Queued> queue = new ArrayList<>();

  private record Queued(String endpoint, Map<String, Object> payload) {}

  public ObtraceClient(ObtraceConfig cfg) {
    if (cfg.apiKey == null || cfg.apiKey.isBlank()) throw new IllegalArgumentException("apiKey required");
    if (cfg.ingestBaseUrl == null || cfg.ingestBaseUrl.isBlank()) throw new IllegalArgumentException("ingestBaseUrl required");
    if (cfg.serviceName == null || cfg.serviceName.isBlank()) throw new IllegalArgumentException("serviceName required");

    this.cfg = cfg;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(cfg.requestTimeoutMs > 0 ? cfg.requestTimeoutMs : 5000))
        .build();
  }

  public synchronized void log(String level, String message, ObtraceContext ctx) {
    enqueue("/otlp/v1/logs", OtlpPayloads.logs(cfg, level, message, ctx));
  }

  public synchronized void metric(String name, double value, String unit, ObtraceContext ctx) {
    enqueue("/otlp/v1/metrics", OtlpPayloads.metrics(cfg, name, value, unit, ctx));
  }

  public synchronized String[] span(
      String name,
      String traceId,
      String spanId,
      Integer statusCode,
      String statusMessage,
      Map<String, Object> attrs
  ) {
    String t = traceId != null && traceId.length() == 32 ? traceId : Propagation.randomHex(16);
    String s = spanId != null && spanId.length() == 16 ? spanId : Propagation.randomHex(8);
    Instant now = Instant.now();
    long nanos = now.getEpochSecond() * 1_000_000_000L + now.getNano();
    enqueue("/otlp/v1/traces", OtlpPayloads.spans(cfg, name, t, s, nanos, nanos, statusCode, statusMessage, attrs));
    return new String[]{t, s};
  }

  public Map<String, String> injectPropagation(Map<String, String> headers, String traceId, String spanId, String sessionId) {
    return Propagation.ensurePropagation(headers, traceId, spanId, sessionId, "traceparent", "x-obtrace-session-id");
  }

  public synchronized void flush() {
    List<Queued> batch = new ArrayList<>(queue);
    queue.clear();
    for (Queued q : batch) {
      send(q);
    }
  }

  public void shutdown() {
    flush();
  }

  private void enqueue(String endpoint, Map<String, Object> payload) {
    int max = cfg.maxQueueSize > 0 ? cfg.maxQueueSize : 1000;
    if (queue.size() >= max) {
      queue.remove(0);
    }
    queue.add(new Queued(endpoint, payload));
  }

  private void send(Queued q) {
    try {
      String json = mapper.writeValueAsString(q.payload);
      HttpRequest.Builder b = HttpRequest.newBuilder()
          .uri(URI.create(cfg.ingestBaseUrl.replaceAll("/$", "") + q.endpoint))
          .timeout(Duration.ofMillis(cfg.requestTimeoutMs > 0 ? cfg.requestTimeoutMs : 5000))
          .header("Authorization", "Bearer " + cfg.apiKey)
          .header("Content-Type", "application/json");
      for (Map.Entry<String, String> h : cfg.defaultHeaders.entrySet()) {
        b.header(h.getKey(), h.getValue());
      }
      HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(json)).build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() >= 300 && cfg.debug) {
        System.err.printf("[obtrace-sdk-java] status=%d endpoint=%s body=%s%n", res.statusCode(), q.endpoint, res.body());
      }
    } catch (IOException | InterruptedException e) {
      if (cfg.debug) {
        System.err.printf("[obtrace-sdk-java] send failed endpoint=%s err=%s%n", q.endpoint, e.getMessage());
      }
      Thread.currentThread().interrupt();
    }
  }
}
