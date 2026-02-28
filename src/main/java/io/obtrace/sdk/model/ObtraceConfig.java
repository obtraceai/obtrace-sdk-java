package io.obtrace.sdk.model;

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
  public boolean debug = false;
  public Map<String, String> defaultHeaders = new HashMap<>();
}
