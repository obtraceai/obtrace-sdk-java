package io.obtrace.sdk.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.obtrace.sdk.http.InstrumentedHttpClient;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ObtraceClient implements AutoCloseable {
  private final ObtraceConfig cfg;
  private final HttpClient http;
  private final ExecutorService executor;
  private final Map<String, String> defaultHeaders;
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<Queued> queue = new ArrayList<>();
  private volatile boolean closed = false;
  private final JulHandler julHandler;
  private final InstrumentedHttpClient instrumentedHttpClient;
  private int circuitFailures = 0;
  private long circuitOpenUntil = 0;

  private record Queued(String endpoint, Map<String, Object> payload) {}

  public ObtraceClient(ObtraceConfig cfg) {
    if (cfg.apiKey() == null || cfg.apiKey().isBlank()) throw new IllegalArgumentException("apiKey required");
    if (cfg.ingestBaseUrl() == null || cfg.ingestBaseUrl().isBlank()) throw new IllegalArgumentException("ingestBaseUrl required");
    if (cfg.serviceName() == null || cfg.serviceName().isBlank()) throw new IllegalArgumentException("serviceName required");

    this.cfg = cfg;
    this.defaultHeaders = cfg.defaultHeaders();
    this.executor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "obtrace-http");
      t.setDaemon(true);
      return t;
    });
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(cfg.requestTimeoutMs() > 0 ? cfg.requestTimeoutMs() : 5000))
        .executor(executor)
        .build();

    this.instrumentedHttpClient = new InstrumentedHttpClient(this);

    this.julHandler = new JulHandler(this);
    this.julHandler.install();

    if (cfg.registerShutdownHook()) {
      Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "obtrace-shutdown"));
    }
  }

  public InstrumentedHttpClient getHttpClient() {
    return instrumentedHttpClient;
  }

  private static String truncate(String s, int max) {
    if (s == null || s.length() <= max) return s;
    return s.substring(0, max) + "...[truncated]";
  }

  public synchronized void log(String level, String message, ObtraceContext ctx) {
    enqueue("/otlp/v1/logs", OtlpPayloads.logs(cfg, level, truncate(message, 32768), ctx));
  }

  public synchronized void metric(String name, double value, String unit, ObtraceContext ctx) {
    if (cfg.validateSemanticMetrics() && cfg.debug() && !SemanticMetrics.isSemanticMetric(name)) {
      System.err.printf("[obtrace-sdk-java] non-canonical metric name: %s%n", name);
    }
    enqueue("/otlp/v1/metrics", OtlpPayloads.metrics(cfg, truncate(name, 1024), value, unit, ctx));
  }

  public synchronized String[] span(
      String name,
      String traceId,
      String spanId,
      Integer statusCode,
      String statusMessage,
      Map<String, Object> attrs
  ) {
    return span(name, traceId, spanId, null, statusCode, statusMessage, attrs);
  }

  public synchronized String[] span(
      String name,
      String traceId,
      String spanId,
      String parentSpanId,
      Integer statusCode,
      String statusMessage,
      Map<String, Object> attrs
  ) {
    String t = traceId != null && traceId.length() == 32 ? traceId : Propagation.randomHex(16);
    String s = spanId != null && spanId.length() == 16 ? spanId : Propagation.randomHex(8);
    String p = parentSpanId != null && parentSpanId.length() == 16 ? parentSpanId : null;
    Instant now = Instant.now();
    long nanos = now.getEpochSecond() * 1_000_000_000L + now.getNano();
    String truncatedName = truncate(name, 32768);
    if (attrs != null) {
      attrs = new java.util.HashMap<>(attrs);
      for (Map.Entry<String, Object> e : attrs.entrySet()) {
        if (e.getValue() instanceof String sv) {
          e.setValue(truncate(sv, 4096));
        }
      }
    }
    enqueue("/otlp/v1/traces", OtlpPayloads.spans(cfg, truncatedName, t, s, p, nanos, nanos, statusCode, statusMessage, attrs));
    return new String[]{t, s};
  }

  public Map<String, String> injectPropagation(Map<String, String> headers, String traceId, String spanId, String sessionId) {
    return Propagation.ensurePropagation(headers, traceId, spanId, sessionId, "traceparent", "x-obtrace-session-id");
  }

  public synchronized void flush() {
    long now = System.currentTimeMillis();
    if (now < circuitOpenUntil) {
      return;
    }
    boolean halfOpen = circuitFailures >= 5;
    List<Queued> batch;
    if (halfOpen) {
      batch = queue.isEmpty() ? List.of() : List.of(queue.remove(0));
    } else {
      batch = new ArrayList<>(queue);
      queue.clear();
    }
    long deadline = System.currentTimeMillis() + (cfg.flushTimeoutMs() > 0 ? cfg.flushTimeoutMs() : 30000);
    for (Queued q : batch) {
      if (System.currentTimeMillis() >= deadline) {
        System.err.printf("[obtrace-sdk-java] flush timeout after %dms, %d items unsent%n", cfg.flushTimeoutMs(), batch.size() - batch.indexOf(q));
        break;
      }
      if (send(q)) {
        if (circuitFailures > 0 && cfg.debug()) {
          System.err.println("[obtrace-sdk-java] circuit breaker closed");
        }
        circuitFailures = 0;
        circuitOpenUntil = 0;
      } else {
        circuitFailures++;
        if (circuitFailures >= 5) {
          circuitOpenUntil = System.currentTimeMillis() + 30000;
          if (cfg.debug()) {
            System.err.println("[obtrace-sdk-java] circuit breaker opened");
          }
          break;
        }
      }
    }
  }

  public void shutdown() {
    flush();
    close();
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    julHandler.uninstall();
    instrumentedHttpClient.close();
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void enqueue(String endpoint, Map<String, Object> payload) {
    int max = cfg.maxQueueSize() > 0 ? cfg.maxQueueSize() : 1000;
    if (queue.size() >= max) {
      queue.remove(0);
      System.err.printf("[obtrace-sdk-java] queue full (%d), dropping oldest telemetry item%n", max);
    }
    queue.add(new Queued(endpoint, payload));
  }

  private boolean send(Queued q) {
    try {
      String json = mapper.writeValueAsString(q.payload);
      HttpRequest.Builder b = HttpRequest.newBuilder()
          .uri(URI.create(cfg.ingestBaseUrl().replaceAll("/$", "") + q.endpoint))
          .timeout(Duration.ofMillis(cfg.requestTimeoutMs() > 0 ? cfg.requestTimeoutMs() : 5000))
          .header("Authorization", "Bearer " + cfg.apiKey())
          .header("Content-Type", "application/json");
      for (Map.Entry<String, String> h : defaultHeaders.entrySet()) {
        b.header(h.getKey(), h.getValue());
      }
      HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(json)).build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() >= 300) {
        if (cfg.debug()) {
          System.err.printf("[obtrace-sdk-java] status=%d endpoint=%s body=%s%n", res.statusCode(), q.endpoint, res.body());
        }
        return false;
      }
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      if (cfg.debug()) {
        System.err.printf("[obtrace-sdk-java] send failed endpoint=%s err=%s%n", q.endpoint, e.getMessage());
      }
      return false;
    }
  }
}
