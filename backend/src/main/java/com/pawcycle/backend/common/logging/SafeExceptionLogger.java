package com.pawcycle.backend.common.logging;

import java.util.Arrays;
import org.slf4j.Logger;

/** HTTP failure diagnostics without exception messages, causes or suppressed exceptions. */
public final class SafeExceptionLogger {
  private SafeExceptionLogger() {}

  public static void error(Logger logger, String eventMessage, Throwable exception) {
    logger.error(
        "{} exceptionType={} stack={}",
        eventMessage,
        exception.getClass().getName(),
        Arrays.toString(exception.getStackTrace()));
  }
}
