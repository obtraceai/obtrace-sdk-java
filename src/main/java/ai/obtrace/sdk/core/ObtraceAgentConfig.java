package ai.obtrace.sdk.core;

public final class ObtraceAgentConfig {

    private static final String DEFAULT_AGENT_JAR = "opentelemetry-javaagent.jar";

    private ObtraceAgentConfig() {}

    private static final String DEFAULT_INGEST_BASE_URL = "https://ingest.obtrace.ai";

    public static String jvmArgs(String apiKey, String serviceName) {
        return jvmArgs(apiKey, DEFAULT_INGEST_BASE_URL, serviceName, DEFAULT_AGENT_JAR);
    }

    public static String jvmArgs(String apiKey, String ingestBaseUrl, String serviceName) {
        return jvmArgs(apiKey, ingestBaseUrl, serviceName, DEFAULT_AGENT_JAR);
    }

    public static String jvmArgs(String apiKey, String ingestBaseUrl, String serviceName, String agentJarPath) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("apiKey required");
        if (serviceName == null || serviceName.isBlank()) throw new IllegalArgumentException("serviceName required");

        String effectiveUrl = (ingestBaseUrl == null || ingestBaseUrl.isBlank()) ? DEFAULT_INGEST_BASE_URL : ingestBaseUrl;
        String endpoint = effectiveUrl.endsWith("/") ? effectiveUrl.substring(0, effectiveUrl.length() - 1) : effectiveUrl;

        return String.join(" ",
            "-javaagent:" + agentJarPath,
            "-Dotel.exporter.otlp.endpoint=" + endpoint,
            "-Dotel.exporter.otlp.headers=Authorization=Bearer " + apiKey,
            "-Dotel.service.name=" + serviceName,
            "-Dotel.exporter.otlp.protocol=http/protobuf"
        );
    }
}
