package io.obtrace.sdk.core;

import io.obtrace.sdk.http.InstrumentedHttpClient;
import io.obtrace.sdk.model.ObtraceConfig;
import io.obtrace.sdk.model.ObtraceContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;

import java.time.Instant;
import java.util.Map;

public class ObtraceClient implements AutoCloseable {
  private final ObtraceConfig cfg;
  private final OpenTelemetrySdk sdk;
  private final Tracer tracer;
  private final Meter meter;
  private final Logger logger;
  private final InstrumentedHttpClient instrumentedHttpClient;
  private final JulHandler julHandler;
  private volatile boolean closed = false;

  public ObtraceClient(ObtraceConfig cfg) {
    if (cfg.apiKey() == null || cfg.apiKey().isBlank()) throw new IllegalArgumentException("apiKey required");
    if (cfg.ingestBaseUrl() == null || cfg.ingestBaseUrl().isBlank()) throw new IllegalArgumentException("ingestBaseUrl required");
    if (cfg.serviceName() == null || cfg.serviceName().isBlank()) throw new IllegalArgumentException("serviceName required");

    this.cfg = cfg;
    this.sdk = OtelSetup.initialize(cfg);
    this.tracer = sdk.getTracer("obtrace-sdk-java", "1.0.0");
    this.meter = sdk.getMeter("obtrace-sdk-java");
    this.logger = sdk.getSdkLoggerProvider().get("obtrace-sdk-java");
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

  public InstrumentedHttpClient wrapHttpClient(java.net.http.HttpClient client) {
    return new InstrumentedHttpClient(this, client);
  }

  public Tracer getTracer() {
    return tracer;
  }

  public Meter getMeter() {
    return meter;
  }

  public Logger getLogger() {
    return logger;
  }

  private static String truncate(String s, int max) {
    if (s == null || s.length() <= max) return s;
    return s.substring(0, max) + "...[truncated]";
  }

  public void log(String level, String message, ObtraceContext ctx) {
    Severity severity = mapSeverity(level);
    var builder = logger.logRecordBuilder()
        .setSeverity(severity)
        .setSeverityText(level.toUpperCase())
        .setBody(truncate(message, 32768));

    if (ctx != null) {
      AttributesBuilder ab = Attributes.builder();
      if (ctx.traceId != null) ab.put("obtrace.trace_id", ctx.traceId);
      if (ctx.spanId != null) ab.put("obtrace.span_id", ctx.spanId);
      if (ctx.sessionId != null) ab.put("obtrace.session_id", ctx.sessionId);
      if (ctx.routeTemplate != null) ab.put("obtrace.route_template", ctx.routeTemplate);
      if (ctx.endpoint != null) ab.put("obtrace.endpoint", ctx.endpoint);
      if (ctx.method != null) ab.put("obtrace.method", ctx.method);
      if (ctx.statusCode != null) ab.put("obtrace.status_code", (long) ctx.statusCode);
      for (Map.Entry<String, Object> e : ctx.attrs.entrySet()) {
        putAttribute(ab, "obtrace.attr." + e.getKey(), e.getValue());
      }
      builder.setAllAttributes(ab.build());
    }

    builder.emit();
  }

  public void metric(String name, double value, String unit, ObtraceContext ctx) {
    if (cfg.validateSemanticMetrics() && cfg.debug() && !SemanticMetrics.isSemanticMetric(name)) {
      System.err.printf("[obtrace-sdk-java] non-canonical metric name: %s%n", name);
    }
    String truncatedName = truncate(name, 1024);
    AttributesBuilder ab = Attributes.builder();
    if (ctx != null) {
      for (Map.Entry<String, Object> e : ctx.attrs.entrySet()) {
        putAttribute(ab, e.getKey(), e.getValue());
      }
    }
    meter.gaugeBuilder(truncatedName)
        .setUnit(unit == null || unit.isBlank() ? "1" : unit)
        .buildWithCallback(measurement -> measurement.record(value, ab.build()));
  }

  public String[] span(
      String name,
      String traceId,
      String spanId,
      Integer statusCode,
      String statusMessage,
      Map<String, Object> attrs
  ) {
    return span(name, traceId, spanId, null, statusCode, statusMessage, attrs);
  }

  public String[] span(
      String name,
      String traceId,
      String spanId,
      String parentSpanId,
      Integer statusCode,
      String statusMessage,
      Map<String, Object> attrs
  ) {
    String truncatedName = truncate(name, 32768);
    var spanBuilder = tracer.spanBuilder(truncatedName)
        .setSpanKind(SpanKind.CLIENT);

    if (attrs != null) {
      for (Map.Entry<String, Object> e : attrs.entrySet()) {
        Object v = e.getValue();
        if (v instanceof String sv) {
          spanBuilder.setAttribute(e.getKey(), truncate(sv, 4096));
        } else if (v instanceof Boolean bv) {
          spanBuilder.setAttribute(e.getKey(), bv);
        } else if (v instanceof Long lv) {
          spanBuilder.setAttribute(e.getKey(), lv);
        } else if (v instanceof Number nv) {
          spanBuilder.setAttribute(e.getKey(), nv.doubleValue());
        } else {
          spanBuilder.setAttribute(e.getKey(), String.valueOf(v));
        }
      }
    }

    Span span = spanBuilder.startSpan();

    if (statusCode != null && statusCode >= 400) {
      span.setStatus(StatusCode.ERROR, statusMessage == null ? "" : statusMessage);
    } else {
      span.setStatus(StatusCode.OK, statusMessage == null ? "" : statusMessage);
    }

    span.end();

    String t = span.getSpanContext().getTraceId();
    String s = span.getSpanContext().getSpanId();
    return new String[]{t, s};
  }

  public void captureError(Throwable throwable, Map<String, Object> attrs) {
    var spanBuilder = tracer.spanBuilder("error")
        .setSpanKind(SpanKind.INTERNAL);
    if (attrs != null) {
      for (Map.Entry<String, Object> e : attrs.entrySet()) {
        Object v = e.getValue();
        if (v instanceof String sv) {
          spanBuilder.setAttribute(e.getKey(), sv);
        } else if (v instanceof Boolean bv) {
          spanBuilder.setAttribute(e.getKey(), bv);
        } else if (v instanceof Long lv) {
          spanBuilder.setAttribute(e.getKey(), lv);
        } else if (v instanceof Number nv) {
          spanBuilder.setAttribute(e.getKey(), nv.doubleValue());
        } else {
          spanBuilder.setAttribute(e.getKey(), String.valueOf(v));
        }
      }
    }
    Span span = spanBuilder.startSpan();
    span.recordException(throwable);
    span.setStatus(StatusCode.ERROR, throwable.getMessage() != null ? throwable.getMessage() : "error");
    span.end();
  }

  public void flush() {
    sdk.getSdkTracerProvider().forceFlush().join(10, java.util.concurrent.TimeUnit.SECONDS);
    sdk.getSdkMeterProvider().forceFlush().join(10, java.util.concurrent.TimeUnit.SECONDS);
    sdk.getSdkLoggerProvider().forceFlush().join(10, java.util.concurrent.TimeUnit.SECONDS);
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
    sdk.close();
  }

  private static Severity mapSeverity(String level) {
    if (level == null) return Severity.INFO;
    return switch (level.toLowerCase()) {
      case "debug", "trace" -> Severity.DEBUG;
      case "info" -> Severity.INFO;
      case "warn", "warning" -> Severity.WARN;
      case "error", "fatal" -> Severity.ERROR;
      default -> Severity.INFO;
    };
  }

  private static void putAttribute(AttributesBuilder ab, String key, Object value) {
    if (value instanceof Boolean b) {
      ab.put(key, b);
    } else if (value instanceof Long l) {
      ab.put(key, l);
    } else if (value instanceof Number n) {
      ab.put(key, n.doubleValue());
    } else {
      ab.put(key, String.valueOf(value));
    }
  }
}
