package ai.obtrace.sdk.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ObtraceConfig {
  private final String apiKey;
  private final String ingestBaseUrl;
  private final String serviceName;
  private final String serviceVersion;
  private final String tenantId;
  private final String projectId;
  private final String appId;
  private final String env;
  private final int requestTimeoutMs;
  private final int maxQueueSize;
  private final int flushTimeoutMs;
  private final boolean validateSemanticMetrics;
  private final boolean debug;
  private final boolean registerShutdownHook;
  private final Map<String, String> defaultHeaders;

  private ObtraceConfig(Builder builder) {
    this.apiKey = builder.apiKey;
    this.ingestBaseUrl = builder.ingestBaseUrl;
    this.serviceName = builder.serviceName;
    this.serviceVersion = builder.serviceVersion;
    this.tenantId = builder.tenantId;
    this.projectId = builder.projectId;
    this.appId = builder.appId;
    this.env = builder.env;
    this.requestTimeoutMs = builder.requestTimeoutMs;
    this.maxQueueSize = builder.maxQueueSize;
    this.flushTimeoutMs = builder.flushTimeoutMs;
    this.validateSemanticMetrics = builder.validateSemanticMetrics;
    this.debug = builder.debug;
    this.registerShutdownHook = builder.registerShutdownHook;
    this.defaultHeaders = Collections.unmodifiableMap(new HashMap<>(builder.defaultHeaders));
  }

  public static Builder builder() {
    return new Builder();
  }

  public String apiKey() { return apiKey; }
  public String ingestBaseUrl() { return ingestBaseUrl; }
  public String serviceName() { return serviceName; }
  public String serviceVersion() { return serviceVersion; }
  public String tenantId() { return tenantId; }
  public String projectId() { return projectId; }
  public String appId() { return appId; }
  public String env() { return env; }
  public int requestTimeoutMs() { return requestTimeoutMs; }
  public int maxQueueSize() { return maxQueueSize; }
  public int flushTimeoutMs() { return flushTimeoutMs; }
  public boolean validateSemanticMetrics() { return validateSemanticMetrics; }
  public boolean debug() { return debug; }
  public boolean registerShutdownHook() { return registerShutdownHook; }
  public Map<String, String> defaultHeaders() { return defaultHeaders; }

  public static final class Builder {
    private String apiKey;
    private String ingestBaseUrl;
    private String serviceName;
    private String serviceVersion = "0.0.0";
    private String tenantId;
    private String projectId;
    private String appId;
    private String env;
    private int requestTimeoutMs = 5000;
    private int maxQueueSize = 1000;
    private int flushTimeoutMs = 30000;
    private boolean validateSemanticMetrics = false;
    private boolean debug = false;
    private boolean registerShutdownHook = true;
    private final Map<String, String> defaultHeaders = new HashMap<>();

    private Builder() {}

    public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
    public Builder ingestBaseUrl(String ingestBaseUrl) { this.ingestBaseUrl = ingestBaseUrl; return this; }
    public Builder serviceName(String serviceName) { this.serviceName = serviceName; return this; }
    public Builder serviceVersion(String serviceVersion) { this.serviceVersion = serviceVersion; return this; }
    public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
    public Builder projectId(String projectId) { this.projectId = projectId; return this; }
    public Builder appId(String appId) { this.appId = appId; return this; }
    public Builder env(String env) { this.env = env; return this; }
    public Builder requestTimeoutMs(int requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; return this; }
    public Builder maxQueueSize(int maxQueueSize) { this.maxQueueSize = maxQueueSize; return this; }
    public Builder flushTimeoutMs(int flushTimeoutMs) { this.flushTimeoutMs = flushTimeoutMs; return this; }
    public Builder validateSemanticMetrics(boolean validateSemanticMetrics) { this.validateSemanticMetrics = validateSemanticMetrics; return this; }
    public Builder debug(boolean debug) { this.debug = debug; return this; }
    public Builder registerShutdownHook(boolean registerShutdownHook) { this.registerShutdownHook = registerShutdownHook; return this; }

    public Builder defaultHeader(String key, String value) {
      this.defaultHeaders.put(key, value);
      return this;
    }

    public Builder defaultHeaders(Map<String, String> headers) {
      this.defaultHeaders.putAll(headers);
      return this;
    }

    public ObtraceConfig build() {
      return new ObtraceConfig(this);
    }
  }
}
