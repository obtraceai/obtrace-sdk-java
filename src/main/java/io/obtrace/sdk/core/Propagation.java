package io.obtrace.sdk.core;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public final class Propagation {
  private static final SecureRandom RNG = new SecureRandom();

  private Propagation() {}

  public static String randomHex(int bytes) {
    byte[] b = new byte[bytes];
    RNG.nextBytes(b);
    StringBuilder sb = new StringBuilder(bytes * 2);
    for (byte value : b) {
      sb.append(String.format("%02x", value));
    }
    return sb.toString();
  }

  public static String createTraceparent(String traceId, String spanId) {
    String t = traceId != null && traceId.length() == 32 ? traceId : randomHex(16);
    String s = spanId != null && spanId.length() == 16 ? spanId : randomHex(8);
    return "00-" + t + "-" + s + "-01";
  }

  public static Map<String, String> ensurePropagation(
      Map<String, String> headers,
      String traceId,
      String spanId,
      String sessionId,
      String traceHeader,
      String sessionHeader
  ) {
    Map<String, String> out = new HashMap<>();
    if (headers != null) {
      out.putAll(headers);
    }
    String th = traceHeader == null || traceHeader.isBlank() ? "traceparent" : traceHeader;
    String sh = sessionHeader == null || sessionHeader.isBlank() ? "x-obtrace-session-id" : sessionHeader;

    if (!containsIgnoreCase(out, th)) {
      out.put(th, createTraceparent(traceId, spanId));
    }
    if (sessionId != null && !sessionId.isBlank() && !containsIgnoreCase(out, sh)) {
      out.put(sh, sessionId);
    }
    return out;
  }

  private static boolean containsIgnoreCase(Map<String, String> map, String key) {
    for (String k : map.keySet()) {
      if (k.equalsIgnoreCase(key)) {
        return true;
      }
    }
    return false;
  }
}
