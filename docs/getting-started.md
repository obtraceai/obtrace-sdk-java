# Getting Started

```java
ObtraceConfig cfg = new ObtraceConfig();
cfg.apiKey = "<API_KEY>";
cfg.ingestBaseUrl = "https://injet.obtrace.ai";
cfg.serviceName = "java-api";

ObtraceClient client = new ObtraceClient(cfg);
client.log("info", "started", null);
client.flush();
```
