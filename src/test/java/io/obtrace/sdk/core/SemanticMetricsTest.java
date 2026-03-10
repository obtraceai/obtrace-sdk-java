package io.obtrace.sdk.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticMetricsTest {
  @Test
  void exposesCanonicalMetricNames() {
    assertEquals("runtime.cpu.utilization", SemanticMetrics.RUNTIME_CPU_UTILIZATION);
    assertEquals("db.operation.latency", SemanticMetrics.DB_OPERATION_LATENCY);
    assertEquals("web.vital.inp", SemanticMetrics.WEB_VITAL_INP);
  }
}
