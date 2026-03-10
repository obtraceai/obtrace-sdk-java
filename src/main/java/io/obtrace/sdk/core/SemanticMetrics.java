package io.obtrace.sdk.core;

import java.util.Set;

public final class SemanticMetrics {
  public static final String THROUGHPUT = "http_requests_total";
  public static final String ERROR_RATE = "http_5xx_total";
  public static final String LATENCY_P95 = "latency_p95";
  public static final String RUNTIME_CPU_UTILIZATION = "runtime.cpu.utilization";
  public static final String RUNTIME_MEMORY_USAGE = "runtime.memory.usage";
  public static final String RUNTIME_THREAD_COUNT = "runtime.thread.count";
  public static final String RUNTIME_GC_PAUSE = "runtime.gc.pause";
  public static final String RUNTIME_EVENTLOOP_LAG = "runtime.eventloop.lag";
  public static final String CLUSTER_CPU_UTILIZATION = "cluster.cpu.utilization";
  public static final String CLUSTER_MEMORY_USAGE = "cluster.memory.usage";
  public static final String CLUSTER_NODE_COUNT = "cluster.node.count";
  public static final String CLUSTER_POD_COUNT = "cluster.pod.count";
  public static final String DB_OPERATION_LATENCY = "db.operation.latency";
  public static final String DB_CLIENT_ERRORS = "db.client.errors";
  public static final String DB_CONNECTIONS_USAGE = "db.connections.usage";
  public static final String MESSAGING_CONSUMER_LAG = "messaging.consumer.lag";
  public static final String WEB_VITAL_LCP = "web.vital.lcp";
  public static final String WEB_VITAL_FCP = "web.vital.fcp";
  public static final String WEB_VITAL_INP = "web.vital.inp";
  public static final String WEB_VITAL_CLS = "web.vital.cls";
  public static final String WEB_VITAL_TTFB = "web.vital.ttfb";
  public static final String USER_ACTIONS = "obtrace.sim.web.react.actions";
  private static final Set<String> ALL = Set.of(
      THROUGHPUT, ERROR_RATE, LATENCY_P95, RUNTIME_CPU_UTILIZATION, RUNTIME_MEMORY_USAGE,
      RUNTIME_THREAD_COUNT, RUNTIME_GC_PAUSE, RUNTIME_EVENTLOOP_LAG, CLUSTER_CPU_UTILIZATION,
      CLUSTER_MEMORY_USAGE, CLUSTER_NODE_COUNT, CLUSTER_POD_COUNT, DB_OPERATION_LATENCY,
      DB_CLIENT_ERRORS, DB_CONNECTIONS_USAGE, MESSAGING_CONSUMER_LAG, WEB_VITAL_LCP,
      WEB_VITAL_FCP, WEB_VITAL_INP, WEB_VITAL_CLS, WEB_VITAL_TTFB, USER_ACTIONS
  );

  private SemanticMetrics() {}

  public static boolean isSemanticMetric(String name) {
    return ALL.contains(name);
  }
}
