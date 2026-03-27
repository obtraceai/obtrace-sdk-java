package io.obtrace.sdk.core;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class JulHandler extends Handler {
  private final ObtraceClient client;

  JulHandler(ObtraceClient client) {
    this.client = client;
  }

  @Override
  public void publish(LogRecord record) {
    if (record == null || !isLoggable(record)) return;
    client.log(mapLevel(record.getLevel()), formatMessage(record), null);
  }

  @Override
  public void flush() {}

  @Override
  public void close() {}

  void install() {
    Logger.getLogger("").addHandler(this);
  }

  void uninstall() {
    Logger.getLogger("").removeHandler(this);
  }

  private static String formatMessage(LogRecord record) {
    String msg = record.getMessage();
    if (msg == null) return "";
    Object[] params = record.getParameters();
    if (params != null && params.length > 0) {
      try {
        return String.format(msg, params);
      } catch (Exception e) {
        return msg;
      }
    }
    return msg;
  }

  private static String mapLevel(Level level) {
    int val = level.intValue();
    if (val <= Level.FINE.intValue()) return "debug";
    if (val <= Level.INFO.intValue()) return "info";
    if (val <= Level.WARNING.intValue()) return "warn";
    return "error";
  }
}
