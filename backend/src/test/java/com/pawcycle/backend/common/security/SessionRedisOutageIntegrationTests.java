package com.pawcycle.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * AUTH-005 failure policy: with {@code redis} selected but the session Redis unreachable, the
 * backend still starts (the warm-up only logs) and anonymous reads work, while every request that
 * needs a session fails instead of falling back to another store.
 */
class SessionRedisOutageIntegrationTests {

  @Test
  void unreachableSessionRedisKeepsAnonymousReadsAndFailsSessionRequestsClosed() {
    // Port 1 is never a Redis.
    try (ConfigurableApplicationContext context =
        SessionStoreTestClient.start(
            "redis",
            "pawcycle.session.redis.port=1",
            "pawcycle.session.redis.connect-timeout=200ms",
            "pawcycle.session.redis.command-timeout=200ms")) {
      SessionStoreTestClient client = new SessionStoreTestClient().use(context);

      assertThat(client.get("/api/products").statusCode()).isEqualTo(200);
      assertThat(client.sessionCookie()).isNull();

      int csrfStatus = client.get("/api/auth/csrf").statusCode();
      assertThat(csrfStatus).isGreaterThanOrEqualTo(400);

      SessionStoreTestClient withCookie =
          new SessionStoreTestClient().use(context).withSessionCookie("c2Vzc2lvbg==");
      assertThat(withCookie.me().statusCode()).isGreaterThanOrEqualTo(400).isNotEqualTo(200);
    }
  }
}
