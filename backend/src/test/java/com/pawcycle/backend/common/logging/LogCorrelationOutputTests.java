package com.pawcycle.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Checks the real log output produced by the application's logging configuration, not only the MDC
 * values: request and scheduled-run ids must appear in text logs and as fields in JSON (ECS) logs.
 * Each case initializes Spring Boot logging with an empty context (no auto-configuration, no DB).
 */
@ExtendWith(OutputCaptureExtension.class)
class LogCorrelationOutputTests {

  @Test
  void textLogsShowTheRequestId(CapturedOutput output) {
    try (ConfigurableApplicationContext ignored = start("")) {
      logWith("requestId", "req-text-1");
    }
    assertThat(output.getOut()).contains("[req-text-1] ").contains("correlation probe");
  }

  @Test
  void textLogsShowTheScheduledRunId(CapturedOutput output) {
    try (ConfigurableApplicationContext ignored = start("")) {
      logWith("jobRunId", "run-text-1");
    }
    assertThat(output.getOut()).contains("[run-text-1] ");
  }

  @Test
  void jsonLogsCarryEveryCorrelationKeyAsAField(CapturedOutput output) {
    try (ConfigurableApplicationContext ignored = start("ecs")) {
      MDC.put("jobName", "checkout-expiration");
      logWith("jobRunId", "run-json-1");
      MDC.clear();
    }
    String line = output.getOut().lines()
        .filter(candidate -> candidate.contains("correlation probe"))
        .findFirst().orElseThrow();
    assertThat(line)
        .startsWith("{")
        .contains("\"jobRunId\":\"run-json-1\"")
        .contains("\"jobName\":\"checkout-expiration\"")
        .contains("\"service\":{\"name\":\"pawcycle-backend\"")
        .contains("\"message\":\"correlation probe\"");
  }

  /** Leaves the shared JVM with the default text configuration for later tests. */
  @AfterAll
  static void restoreTextLogging() {
    start("").close();
  }

  private static ConfigurableApplicationContext start(String format) {
    return new SpringApplicationBuilder(Empty.class)
        .web(WebApplicationType.NONE)
        .properties("PAWCYCLE_LOG_FORMAT=" + format, "spring.main.banner-mode=off")
        .run();
  }

  private static void logWith(String key, String value) {
    MDC.put(key, value);
    try {
      LoggerFactory.getLogger(LogCorrelationOutputTests.class).info("correlation probe");
    } finally {
      MDC.remove(key);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Empty {}
}
