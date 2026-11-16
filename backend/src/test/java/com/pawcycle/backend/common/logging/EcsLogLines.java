package com.pawcycle.backend.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Renders one log event with Spring Boot's ECS encoder in an isolated Logback context, so tests can
 * check the JSON shape without re-initializing the shared logging system (which Spring Boot skips
 * while other test contexts are open).
 */
final class EcsLogLines {
  private EcsLogLines() {}

  static String render(String loggerName, String message, Map<String, String> mdc, List<KeyValuePair> pairs) {
    LoggerContext context = new LoggerContext();
    context.putObject(Environment.class.getName(),
        new MockEnvironment()
            .withProperty("spring.application.name", "pawcycle-backend")
            .withProperty("logging.structured.ecs.service.name", "pawcycle-backend"));
    StructuredLogEncoder encoder = new StructuredLogEncoder();
    encoder.setContext(context);
    encoder.setFormat("ecs");
    encoder.start();
    LoggingEvent event = new LoggingEvent();
    event.setLoggerContext(context);
    event.setLoggerName(loggerName);
    event.setLevel(Level.INFO);
    event.setMessage(message);
    event.setThreadName("test");
    event.setTimeStamp(System.currentTimeMillis());
    event.setMDCPropertyMap(mdc);
    pairs.forEach(event::addKeyValuePair);
    return new String(encoder.encode(event), StandardCharsets.UTF_8).strip();
  }
}
