package io.obtrace.sdk.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropagationTest {
  @Test
  void createTraceparentUsesProvidedIds() {
    String traceparent = Propagation.createTraceparent(
        "0123456789abcdef0123456789abcdef",
        "0123456789abcdef"
    );
    assertEquals("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", traceparent);
  }

  @Test
  void ensurePropagationKeepsExistingHeader() {
    Map<String, String> headers = Propagation.ensurePropagation(
        Map.of("traceparent", "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"),
        "0123456789abcdef0123456789abcdef",
        "0123456789abcdef",
        "session-1",
        "traceparent",
        "x-obtrace-session-id"
    );
    assertEquals("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01", headers.get("traceparent"));
    assertEquals("session-1", headers.get("x-obtrace-session-id"));
    assertTrue(headers.size() >= 2);
  }
}
