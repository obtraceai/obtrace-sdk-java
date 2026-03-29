package ai.obtrace.sdk.core;

import ai.obtrace.sdk.model.ObtraceConfig;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ResourceAttributes;

import java.time.Duration;
import java.util.Map;

public final class OtelSetup {
  private OtelSetup() {}

  public static OpenTelemetrySdk initialize(ObtraceConfig cfg) {
    String baseUrl = cfg.ingestBaseUrl().replaceAll("/$", "");
    String authHeader = "Bearer " + cfg.apiKey();

    Resource resource = Resource.getDefault().merge(Resource.create(buildResourceAttributes(cfg)));

    OtlpHttpSpanExporter spanExporter = buildSpanExporter(baseUrl, authHeader, cfg);
    OtlpHttpMetricExporter metricExporter = buildMetricExporter(baseUrl, authHeader, cfg);
    OtlpHttpLogRecordExporter logExporter = buildLogExporter(baseUrl, authHeader, cfg);

    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
        .build();

    SdkMeterProvider meterProvider = SdkMeterProvider.builder()
        .setResource(resource)
        .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
            .setInterval(Duration.ofSeconds(15))
            .build())
        .build();

    SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
        .setResource(resource)
        .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
        .build();

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setMeterProvider(meterProvider)
        .setLoggerProvider(loggerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .build();
  }

  private static OtlpHttpSpanExporter buildSpanExporter(String baseUrl, String authHeader, ObtraceConfig cfg) {
    var builder = OtlpHttpSpanExporter.builder()
        .setEndpoint(baseUrl + "/otlp/v1/traces")
        .addHeader("Authorization", authHeader)
        .setTimeout(Duration.ofMillis(cfg.requestTimeoutMs() > 0 ? cfg.requestTimeoutMs() : 5000));
    for (Map.Entry<String, String> h : cfg.defaultHeaders().entrySet()) {
      builder.addHeader(h.getKey(), h.getValue());
    }
    return builder.build();
  }

  private static OtlpHttpMetricExporter buildMetricExporter(String baseUrl, String authHeader, ObtraceConfig cfg) {
    var builder = OtlpHttpMetricExporter.builder()
        .setEndpoint(baseUrl + "/otlp/v1/metrics")
        .addHeader("Authorization", authHeader)
        .setTimeout(Duration.ofMillis(cfg.requestTimeoutMs() > 0 ? cfg.requestTimeoutMs() : 5000));
    for (Map.Entry<String, String> h : cfg.defaultHeaders().entrySet()) {
      builder.addHeader(h.getKey(), h.getValue());
    }
    return builder.build();
  }

  private static OtlpHttpLogRecordExporter buildLogExporter(String baseUrl, String authHeader, ObtraceConfig cfg) {
    var builder = OtlpHttpLogRecordExporter.builder()
        .setEndpoint(baseUrl + "/otlp/v1/logs")
        .addHeader("Authorization", authHeader)
        .setTimeout(Duration.ofMillis(cfg.requestTimeoutMs() > 0 ? cfg.requestTimeoutMs() : 5000));
    for (Map.Entry<String, String> h : cfg.defaultHeaders().entrySet()) {
      builder.addHeader(h.getKey(), h.getValue());
    }
    return builder.build();
  }

  @SuppressWarnings("deprecation")
  private static Attributes buildResourceAttributes(ObtraceConfig cfg) {
    var builder = Attributes.builder()
        .put(ResourceAttributes.SERVICE_NAME, cfg.serviceName())
        .put(ResourceAttributes.SERVICE_VERSION, cfg.serviceVersion() == null ? "0.0.0" : cfg.serviceVersion())
        .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, cfg.env() == null ? "dev" : cfg.env())
        .put("runtime.name", "java");
    if (cfg.tenantId() != null) builder.put("obtrace.tenant_id", cfg.tenantId());
    if (cfg.projectId() != null) builder.put("obtrace.project_id", cfg.projectId());
    if (cfg.appId() != null) builder.put("obtrace.app_id", cfg.appId());
    if (cfg.env() != null) builder.put("obtrace.env", cfg.env());
    return builder.build();
  }
}
