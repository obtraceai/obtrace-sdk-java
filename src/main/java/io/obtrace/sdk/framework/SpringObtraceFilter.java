package io.obtrace.sdk.framework;

import io.obtrace.sdk.core.ObtraceClient;
import io.obtrace.sdk.model.ObtraceContext;

import java.time.Duration;
import java.time.Instant;

public class SpringObtraceFilter {
  private final ObtraceClient client;

  public SpringObtraceFilter(ObtraceClient client) {
    this.client = client;
  }

  public void afterRequest(String method, String path, int statusCode, Instant startedAt) {
    ObtraceContext ctx = new ObtraceContext();
    ctx.method = method;
    ctx.endpoint = path;
    ctx.statusCode = statusCode;
    ctx.attrs.put("duration_ms", Duration.between(startedAt, Instant.now()).toMillis());
    client.log("info", "spring request complete", ctx);
  }
}
