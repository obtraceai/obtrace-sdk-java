package ai.obtrace.sdk.core;

public final class ObtraceAgentConfig {

    private static final String DEFAULT_AGENT_JAR = "opentelemetry-javaagent.jar";

    private ObtraceAgentConfig() {}

    public static String jvmArgs(String apiKey, String ingestBaseUrl, String serviceName) {
        return jvmArgs(apiKey, ingestBaseUrl, serviceName, DEFAULT_AGENT_JAR);
    }

    public static String jvmArgs(String apiKey, String ingestBaseUrl, String serviceName, String agentJarPath) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("apiKey required");
        if (ingestBaseUrl == null || ingestBaseUrl.isBlank()) throw new IllegalArgumentException("ingestBaseUrl required");
        if (serviceName == null || serviceName.isBlank()) throw new IllegalArgumentException("serviceName required");

        String endpoint = ingestBaseUrl.endsWith("/") ? ingestBaseUrl.substring(0, ingestBaseUrl.length() - 1) : ingestBaseUrl;

        return String.join(" ",
            "-javaagent:" + agentJarPath,
            "-Dotel.exporter.otlp.endpoint=" + endpoint,
            "-Dotel.exporter.otlp.headers=Authorization=Bearer " + apiKey,
            "-Dotel.service.name=" + serviceName,
            "-Dotel.exporter.otlp.protocol=http/protobuf"
        );
    }
}
