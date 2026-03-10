import io.obtrace.sdk.core.ObtraceClient;
import io.obtrace.sdk.core.SemanticMetrics;
import io.obtrace.sdk.model.ObtraceConfig;

import java.util.Map;

public class Main {
  public static void main(String[] args) {
    ObtraceConfig cfg = new ObtraceConfig();
    cfg.apiKey = "devkey";
    cfg.ingestBaseUrl = "https://inject.obtrace.ai";
    cfg.serviceName = "java-example";
    cfg.tenantId = "tenant-dev";
    cfg.projectId = "project-dev";
    cfg.appId = "java";
    cfg.env = "dev";
    cfg.debug = true;

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
