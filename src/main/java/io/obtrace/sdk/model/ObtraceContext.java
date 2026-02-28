package io.obtrace.sdk.model;

import java.util.HashMap;
import java.util.Map;

public class ObtraceContext {
  public String traceId;
  public String spanId;
  public String sessionId;
  public String routeTemplate;
  public String endpoint;
  public String method;
  public Integer statusCode;
  public Map<String, Object> attrs = new HashMap<>();
}
