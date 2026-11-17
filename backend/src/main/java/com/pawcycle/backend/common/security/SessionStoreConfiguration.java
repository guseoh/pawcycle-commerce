package com.pawcycle.backend.common.security;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.SpringSessionRedisConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.RedisHttpSessionConfiguration;

/**
 * Chooses where HTTP sessions (SecurityContext and CSRF token) live (AUTH-005).
 *
 * <p>{@code memory} keeps the servlet container session. {@code redis} stores sessions in a
 * session-only Redis that is separate from the product-list cache Redis, so that a backend
 * restart or re-creation keeps logins and CSRF tokens. Spring Boot's own Redis session
 * auto-configuration is excluded because it would bind sessions to the cache Redis whenever
 * that connection exists.
 */
@Configuration(proxyBeanMethods = false)
public class SessionStoreConfiguration {

  static final String STORE_PROPERTY = "pawcycle.session.store";
  static final String REDIS_NAMESPACE = "pawcycle:session";

  SessionStoreConfiguration(@Value("${" + STORE_PROPERTY + ":memory}") String store) {
    if (!"memory".equals(store) && !"redis".equals(store)) {
      throw new IllegalStateException(STORE_PROPERTY + " must be 'memory' or 'redis'");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnProperty(name = STORE_PROPERTY, havingValue = "redis")
  @Import(RedisHttpSessionConfiguration.class)
  static class RedisSessionStoreConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStoreConfiguration.class);

    /**
     * Not a default candidate, so the cache's auto-configured connection factory stays in place
     * and only Spring Session (via the qualifier) uses this one.
     */
    @Bean(defaultCandidate = false)
    @SpringSessionRedisConnectionFactory
    LettuceConnectionFactory sessionRedisConnectionFactory(
        @Value("${pawcycle.session.redis.host}") String host,
        @Value("${pawcycle.session.redis.port}") int port,
        @Value("${pawcycle.session.redis.connect-timeout}") Duration connectTimeout,
        @Value("${pawcycle.session.redis.command-timeout}") Duration commandTimeout) {
      LettuceClientConfiguration client =
          LettuceClientConfiguration.builder()
              .commandTimeout(commandTimeout)
              .clientOptions(
                  ClientOptions.builder()
                      .socketOptions(
                          SocketOptions.builder()
                              .connectTimeout(connectTimeout)
                              .build())
                      .build())
              .build();
      return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), client);
    }

    /** Keeps the approved 30-minute idle timeout and a project key namespace. */
    @Bean
    SessionRepositoryCustomizer<RedisSessionRepository> pawcycleSessionRepositoryCustomizer(
        @Value("${server.servlet.session.timeout}") Duration timeout) {
      return repository -> {
        repository.setDefaultMaxInactiveInterval(timeout);
        repository.setRedisKeyNamespace(REDIS_NAMESPACE);
      };
    }

    /**
     * Opens the shared session-Redis connection once the application is ready, so that the first
     * login after a deploy does not pay the connection initialization cost inside the 1s command
     * timeout (observed up to above 1s after an idle start in local-integration). A failure only
     * logs: the backend still serves anonymous reads, and session requests fail closed until
     * Redis is reachable.
     */
    @Bean
    ApplicationListener<ApplicationReadyEvent> sessionRedisConnectionWarmUp(
        @Qualifier("sessionRedisConnectionFactory") LettuceConnectionFactory connectionFactory) {
      return event -> {
        try (RedisConnection connection = connectionFactory.getConnection()) {
          connection.ping();
        } catch (RuntimeException exception) {
          log.warn(
              "Session Redis is not reachable at startup; session requests fail until it is: {}",
              exception.getClass().getSimpleName());
        }
      };
    }
  }
}
