package com.pawcycle.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.pawcycle.backend.common.security.SessionStoreTestClient.TestMember;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;

/**
 * AUTH-005: with {@code pawcycle.session.store=redis} the login SecurityContext and CSRF token
 * survive a full application-context re-creation. Needs a disposable session Redis given by
 * {@code PAWCYCLE_SESSION_REDIS_HOST}/{@code PAWCYCLE_SESSION_REDIS_PORT}; CI must provide it.
 */
class RedisSessionStoreIntegrationTests {

  private static final String SESSION_KEY_PREFIX =
      SessionStoreConfiguration.REDIS_NAMESPACE + ":sessions:";

  @BeforeAll
  static void requireSessionRedis() {
    if (System.getenv("PAWCYCLE_SESSION_REDIS_PORT") != null) {
      return;
    }
    if ("true".equalsIgnoreCase(System.getenv("CI"))) {
      fail("CI must provide a disposable session Redis via PAWCYCLE_SESSION_REDIS_PORT");
    }
    Assumptions.abort("PAWCYCLE_SESSION_REDIS_PORT is not set; session Redis tests skipped locally");
  }

  @Test
  void loginAndCsrfTokenSurviveApplicationContextRecreation() {
    SessionStoreTestClient client = new SessionStoreTestClient();
    TestMember member;
    String sessionIdAfterLogin;

    try (ConfigurableApplicationContext first = SessionStoreTestClient.start("redis")) {
      assertSessionRepositoryIsSeparateFromCache(first);
      member = SessionStoreTestClient.createMember(first);
      client.use(first);

      client.fetchCsrfToken();
      String anonymousSessionCookie = client.sessionCookie();
      assertThat(anonymousSessionCookie).isNotNull();

      assertThat(client.login(member).statusCode()).isEqualTo(200);
      assertThat(client.sessionCookie()).isNotEqualTo(anonymousSessionCookie);
      sessionIdAfterLogin = sessionId(client.sessionCookie());
      client.fetchCsrfToken();

      assertThat(sessionKeyExists(first, sessionIdAfterLogin)).isTrue();
      assertThat(sessionKeyExists(first, sessionId(anonymousSessionCookie))).isFalse();
    }

    try (ConfigurableApplicationContext second = SessionStoreTestClient.start("redis")) {
      client.use(second);
      try {
        HttpResponse<String> me = client.me();
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"memberId\":" + member.id());

        assertThat(client.createAddress(false).statusCode()).isEqualTo(403);
        assertThat(client.createAddress(true).statusCode()).isEqualTo(201);

        assertThat(client.logout().statusCode()).isEqualTo(204);
        assertThat(sessionKeyExists(second, sessionIdAfterLogin)).isFalse();
        assertThat(client.sessionCookie()).isNull();

        // A replayed pre-logout cookie must not authenticate either.
        SessionStoreTestClient replay =
            new SessionStoreTestClient()
                .use(second)
                .withSessionCookie(
                    Base64.getEncoder()
                        .encodeToString(sessionIdAfterLogin.getBytes(StandardCharsets.UTF_8)));
        assertThat(replay.me().statusCode()).isEqualTo(401);
      } finally {
        SessionStoreTestClient.deleteMember(second, member.id());
      }
    }

    SessionStoreTestClient.assertSessionCookieContract(client);
  }

  private static void assertSessionRepositoryIsSeparateFromCache(
      ConfigurableApplicationContext context) {
    assertThat(context.getBean(SessionRepository.class)).isInstanceOf(RedisSessionRepository.class);
    LettuceConnectionFactory session =
        context.getBean("sessionRedisConnectionFactory", LettuceConnectionFactory.class);
    RedisConnectionFactory cache = context.getBean(RedisConnectionFactory.class);
    assertThat(cache).isNotSameAs(session);
    assertThat(session.getPort())
        .isEqualTo(Integer.parseInt(System.getenv("PAWCYCLE_SESSION_REDIS_PORT")));
  }

  private static boolean sessionKeyExists(ConfigurableApplicationContext context, String sessionId) {
    LettuceConnectionFactory session =
        context.getBean("sessionRedisConnectionFactory", LettuceConnectionFactory.class);
    return Boolean.TRUE.equals(new StringRedisTemplate(session).hasKey(SESSION_KEY_PREFIX + sessionId));
  }

  /** Spring Session's cookie value is the Base64-encoded session id. */
  private static String sessionId(String cookieValue) {
    return new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
  }
}
