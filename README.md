# obtrace-sdk-java

Java backend SDK for Obtrace telemetry transport and instrumentation.

## Scope
- OTLP logs/traces/metrics transport
- Context propagation
- Outbound HTTP instrumentation (`InstrumentedHttpClient`)
- Framework helper baseline (`SpringObtraceFilter`)

## Design Principle
SDK is thin/dumb.
- No business logic authority in client SDK.
- Policy and product logic are server-side.

## Install

```xml
<dependency>
  <groupId>ai.obtrace</groupId>
  <artifactId>obtrace-sdk-java</artifactId>
  <version>1.0.2</version>
</dependency>
```

Current workspace build:

```bash
mvn -q -DskipTests package
```

## Build

```bash
mvn -q -DskipTests package
```

## Configuration

Required:
- `apiKey`
- `serviceName`

Optional (auto-resolved from API key on the server side):
- `ingestBaseUrl` (defaults to `https://ingest.obtrace.ai`)
- `tenantId`
- `projectId`
- `appId`
- `env`
- `serviceVersion`

## Zero-Config (Java Agent)

For full auto-instrumentation (JDBC, Spring, Kafka, gRPC, OkHttp, and more), use the OpenTelemetry Java agent pointed at Obtrace. No SDK code required:

```bash
# Download the agent once
curl -Lo opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# Run your application with auto-instrumentation
java -javaagent:opentelemetry-javaagent.jar \
  -Dotel.exporter.otlp.endpoint=https://ingest.obtrace.ai \
  -Dotel.exporter.otlp.headers="Authorization=Bearer obt_live_..." \
  -Dotel.service.name=my-service \
  -Dotel.exporter.otlp.protocol=http/protobuf \
  -jar my-app.jar
```

Or generate the JVM args programmatically:

```java
String args = ObtraceAgentConfig.jvmArgs(
    "obt_live_...",
    "my-service"
);
```

Or get the equivalent environment variables from a config object:

```java
ObtraceConfig cfg = ObtraceConfig.builder()
    .apiKey("obt_live_...")
    .serviceName("my-service")
    .build();

Map<String, String> env = ObtraceClient.otelEnvironmentVars(cfg);
// OTEL_EXPORTER_OTLP_ENDPOINT, OTEL_EXPORTER_OTLP_HEADERS,
// OTEL_SERVICE_NAME, OTEL_EXPORTER_OTLP_PROTOCOL
```

## Quickstart

### Simplified setup

The API key resolves `tenant_id`, `project_id`, `app_id`, and `env` automatically on the server side, so only two fields are needed:

```java
ObtraceConfig cfg = ObtraceConfig.builder()
    .apiKey("obt_live_...")
    .serviceName("my-service")
    .build();

ObtraceClient client = new ObtraceClient(cfg);
```

### Full configuration

For advanced use cases you can override the resolved values explicitly:

```java
import ai.obtrace.sdk.core.SemanticMetrics;
import java.util.Map;

ObtraceConfig cfg = ObtraceConfig.builder()
    .apiKey("<API_KEY>")
    .serviceName("java-api")
    .build();

ObtraceClient client = new ObtraceClient(cfg);
client.log("info", "started", null);
client.metric(SemanticMetrics.RUNTIME_CPU_UTILIZATION, 0.41, "1", null);
client.span("checkout.charge", null, null, null, "", Map.of(
    "feature.name", "checkout",
    "payment.provider", "stripe"
));
client.flush();
```

## Canonical metrics and custom spans

- Use `SemanticMetrics.*` for globally normalized metric names.
- Custom spans use `client.span(..., attrs)` and should carry business detail in attributes.
- Keep free-form metric names only for application-specific signals outside the shared catalog.

## Frameworks and HTTP

- Spring baseline helper: `SpringObtraceFilter`
- Outbound client helper: `InstrumentedHttpClient`
- Reference docs:
  - `docs/frameworks.md`
  - `docs/http-client.md`

## Production Hardening

1. Keep API keys in env/secret stores (not source code).
2. Use separate keys for staging and production.
3. Ensure flush on controlled shutdown.
4. Validate telemetry and trace propagation after deploy.

## Troubleshooting

- No ingest: confirm `ingestBaseUrl` and service egress policy.
- Missing trace links: validate `traceparent` propagation in outbound calls.
- Debug transport failures with SDK debug logging in non-production.

## Documentation
- Docs index: `docs/index.md`
- LLM context file: `llm.txt`
- MCP metadata: `mcp.json`

## Reference
