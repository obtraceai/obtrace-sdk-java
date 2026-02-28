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
<!-- Maven dependency placeholder -->
<dependency>
  <groupId>io.obtrace</groupId>
  <artifactId>obtrace-sdk-java</artifactId>
  <version>1.0.0</version>
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
- `ingestBaseUrl`
- `serviceName`

Recommended:
- `tenantId`
- `projectId`
- `appId`
- `env`
- `serviceVersion`

## Quickstart

```java
ObtraceConfig cfg = new ObtraceConfig();
cfg.apiKey = "<API_KEY>";
cfg.ingestBaseUrl = "https://injet.obtrace.ai";
cfg.serviceName = "java-api";

ObtraceClient client = new ObtraceClient(cfg);
client.log("info", "started", null);
client.flush();
```

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
