import io.obtrace.sdk.core.ObtraceClient;
import io.obtrace.sdk.model.ObtraceConfig;

public class Main {
  public static void main(String[] args) {
    ObtraceConfig cfg = new ObtraceConfig();
    cfg.apiKey = "devkey";
    cfg.ingestBaseUrl = "https://injet.obtrace.ai";
    cfg.serviceName = "java-example";
    cfg.tenantId = "tenant-dev";
    cfg.projectId = "project-dev";
    cfg.appId = "java";
    cfg.env = "dev";
    cfg.debug = true;

    ObtraceClient client = new ObtraceClient(cfg);
    client.log("info", "java sdk initialized", null);
    client.metric("java.example.metric", 1, "1", null);
    client.span("java.example.span", null, null, null, "", null);
    client.flush();
  }
}
