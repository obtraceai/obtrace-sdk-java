package io.obtrace.sdk.framework;

import io.obtrace.sdk.core.ObtraceClient;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.time.Duration;
import java.time.Instant;

public class SpringObtraceFilter {
  private final Tracer tracer;

  public SpringObtraceFilter(ObtraceClient client) {
    this.tracer = client.getTracer();
  }

  public Span startRequest(String method, String path) {
    return tracer.spanBuilder(method + " " + path)
        .setSpanKind(SpanKind.SERVER)
        .setAttribute("http.method", method)
        .setAttribute("http.target", path)
        .startSpan();
  }

  public void endRequest(Span span, int statusCode) {
    span.setAttribute("http.status_code", (long) statusCode);
    if (statusCode >= 400) {
      span.setStatus(StatusCode.ERROR, "HTTP " + statusCode);
    } else {
      span.setStatus(StatusCode.OK);
    }
    span.end();
  }
}
