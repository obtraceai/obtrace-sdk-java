package io.obtrace.sdk.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ObtraceConfig {
  public String apiKey;
  public String ingestBaseUrl;
  public String serviceName;
  public String serviceVersion = "0.0.0";
  public String tenantId;
  public String projectId;
  public String appId;
  public String env;
  public int requestTimeoutMs = 5000;
  public int maxQueueSize = 1000;
  public int flushTimeoutMs = 30000;
  public boolean validateSemanticMetrics = false;
  public boolean debug = false;
  public boolean registerShutdownHook = true;
  private Map<String, String> defaultHeaders = new HashMap<>();

  public Map<String, String> getDefaultHeaders() {
    return Collections.unmodifiableMap(defaultHeaders);
  }

  public void setDefaultHeaders(Map<String, String> headers) {
    this.defaultHeaders = new HashMap<>(headers);
  }

  public void addDefaultHeader(String key, String value) {
    this.defaultHeaders.put(key, value);
  }
}
