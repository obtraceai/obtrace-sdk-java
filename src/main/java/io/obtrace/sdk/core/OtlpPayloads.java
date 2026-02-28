package io.obtrace.sdk.core;

import io.obtrace.sdk.model.ObtraceConfig;
import io.obtrace.sdk.model.ObtraceContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OtlpPayloads {
  private OtlpPayloads() {}

  public static Map<String, Object> logs(ObtraceConfig cfg, String level, String body, ObtraceContext ctx) {
    Map<String, Object> cattrs = new HashMap<>();
    cattrs.put("obtrace.log.level", level);
    if (ctx != null) {
      if (ctx.traceId != null) cattrs.put("obtrace.trace_id", ctx.traceId);
      if (ctx.spanId != null) cattrs.put("obtrace.span_id", ctx.spanId);
      if (ctx.sessionId != null) cattrs.put("obtrace.session_id", ctx.sessionId);
      if (ctx.routeTemplate != null) cattrs.put("obtrace.route_template", ctx.routeTemplate);
      if (ctx.endpoint != null) cattrs.put("obtrace.endpoint", ctx.endpoint);
      if (ctx.method != null) cattrs.put("obtrace.method", ctx.method);
      if (ctx.statusCode != null) cattrs.put("obtrace.status_code", ctx.statusCode);
      for (Map.Entry<String, Object> e : ctx.attrs.entrySet()) {
        cattrs.put("obtrace.attr." + e.getKey(), e.getValue());
      }
    }

    Map<String, Object> rec = new HashMap<>();
    rec.put("timeUnixNano", String.valueOf(nowNanos()));
    rec.put("severityText", level.toUpperCase());
    rec.put("body", Map.of("stringValue", body));
    rec.put("attributes", attrs(cattrs));

    return Map.of("resourceLogs", List.of(
        Map.of(
            "resource", Map.of("attributes", resource(cfg)),
            "scopeLogs", List.of(Map.of(
                "scope", Map.of("name", "obtrace-sdk-java", "version", "1.0.0"),
                "logRecords", List.of(rec)
            ))
        )
    ));
  }

  public static Map<String, Object> metrics(ObtraceConfig cfg, String name, double value, String unit, ObtraceContext ctx) {
    String u = unit == null || unit.isBlank() ? "1" : unit;
    Map<String, Object> dp = new HashMap<>();
    dp.put("timeUnixNano", String.valueOf(nowNanos()));
    dp.put("asDouble", value);
    dp.put("attributes", attrs(ctx == null ? Map.of() : ctx.attrs));

    return Map.of("resourceMetrics", List.of(
        Map.of(
            "resource", Map.of("attributes", resource(cfg)),
            "scopeMetrics", List.of(Map.of(
                "scope", Map.of("name", "obtrace-sdk-java", "version", "1.0.0"),
                "metrics", List.of(Map.of(
                    "name", name,
                    "unit", u,
                    "gauge", Map.of("dataPoints", List.of(dp))
                ))
            ))
        )
    ));
  }

  public static Map<String, Object> spans(
      ObtraceConfig cfg,
      String name,
      String traceId,
      String spanId,
      long startNanos,
      long endNanos,
      Integer statusCode,
      String statusMessage,
      Map<String, Object> attrs
  ) {
    int code = statusCode != null && statusCode >= 400 ? 2 : 1;

    Map<String, Object> span = new HashMap<>();
    span.put("traceId", traceId);
    span.put("spanId", spanId);
    span.put("name", name);
    span.put("kind", 3);
    span.put("startTimeUnixNano", String.valueOf(startNanos));
    span.put("endTimeUnixNano", String.valueOf(endNanos));
    span.put("attributes", attrs(attrs == null ? Map.of() : attrs));
    span.put("status", Map.of("code", code, "message", statusMessage == null ? "" : statusMessage));

    return Map.of("resourceSpans", List.of(
        Map.of(
            "resource", Map.of("attributes", resource(cfg)),
            "scopeSpans", List.of(Map.of(
                "scope", Map.of("name", "obtrace-sdk-java", "version", "1.0.0"),
                "spans", List.of(span)
            ))
        )
    ));
  }

  private static List<Map<String, Object>> resource(ObtraceConfig cfg) {
    Map<String, Object> base = new HashMap<>();
    base.put("service.name", cfg.serviceName);
    base.put("service.version", cfg.serviceVersion == null ? "0.0.0" : cfg.serviceVersion);
    base.put("deployment.environment", cfg.env == null ? "dev" : cfg.env);
    base.put("runtime.name", "java");
    if (cfg.tenantId != null) base.put("obtrace.tenant_id", cfg.tenantId);
    if (cfg.projectId != null) base.put("obtrace.project_id", cfg.projectId);
    if (cfg.appId != null) base.put("obtrace.app_id", cfg.appId);
    if (cfg.env != null) base.put("obtrace.env", cfg.env);
    return attrs(base);
  }

  private static List<Map<String, Object>> attrs(Map<String, Object> in) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, Object> e : in.entrySet()) {
      out.add(Map.of("key", e.getKey(), "value", valueObj(e.getValue())));
    }
    return out;
  }

  private static Map<String, Object> valueObj(Object v) {
    if (v instanceof Boolean b) return Map.of("boolValue", b);
    if (v instanceof Number n) return Map.of("doubleValue", n.doubleValue());
    return Map.of("stringValue", String.valueOf(v));
  }

  private static long nowNanos() {
    Instant now = Instant.now();
    return now.getEpochSecond() * 1_000_000_000L + now.getNano();
  }
}
