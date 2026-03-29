import ai.obtrace.sdk.core.ObtraceClient;
import ai.obtrace.sdk.core.SemanticMetrics;
import ai.obtrace.sdk.model.ObtraceConfig;

import java.util.Map;

public class Main {
  public static void main(String[] args) {
    ObtraceConfig cfg = ObtraceConfig.builder()
        .apiKey("devkey")
        .ingestBaseUrl("https://inject.obtrace.ai")
        .serviceName("java-example")
        .tenantId("tenant-dev")
        .projectId("project-dev")
        .appId("java")
        .env("dev")
        .debug(true)
        .build();

    ObtraceClient client = new ObtraceClient(cfg);
    client.log("info", "java sdk initialized", null);
    client.metric(SemanticMetrics.RUNTIME_CPU_UTILIZATION, 0.41, "1", null);
    client.span("checkout.charge", null, null, null, "", Map.of(
        "feature.name", "checkout",
        "payment.provider", "stripe"
    ));
    client.flush();
  }
}
