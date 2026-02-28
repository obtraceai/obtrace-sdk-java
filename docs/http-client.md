# HTTP Client Instrumentation

Use `InstrumentedHttpClient`:

```java
InstrumentedHttpClient hc = new InstrumentedHttpClient(client);
HttpResponse<String> res = hc.send("GET", "https://httpbin.org/status/200", null, Map.of());
```

This injects propagation headers and logs outbound request telemetry.
