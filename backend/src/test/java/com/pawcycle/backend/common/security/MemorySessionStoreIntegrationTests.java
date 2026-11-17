package com.pawcycle.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.common.security.SessionStoreTestClient.TestMember;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.session.SessionRepository;

/**
 * AUTH-005: the production default {@code memory} must start and authenticate without any session
 * Redis, so the code can ship before production Redis exists.
 */
class MemorySessionStoreIntegrationTests {

  @Test
  void memoryStoreStartsAndAuthenticatesWithoutSessionRedis() {
    // Port 1 is never a Redis; any connection attempt would fail the login below.
    try (ConfigurableApplicationContext context =
        SessionStoreTestClient.start("memory", "pawcycle.session.redis.port=1")) {
      assertThat(context.getBeanNamesForType(SessionRepository.class)).isEmpty();
      assertThat(context.containsBean("sessionRedisConnectionFactory")).isFalse();
      assertThat(context.containsBean("springSessionRepositoryFilter")).isFalse();

      TestMember member = SessionStoreTestClient.createMember(context);
      try {
        SessionStoreTestClient client = new SessionStoreTestClient().use(context);
        client.fetchCsrfToken();
        assertThat(client.login(member).statusCode()).isEqualTo(200);
        client.fetchCsrfToken();
        assertThat(client.me().statusCode()).isEqualTo(200);
        assertThat(client.createAddress(true).statusCode()).isEqualTo(201);
        assertThat(client.logout().statusCode()).isEqualTo(204);
        assertThat(client.me().statusCode()).isEqualTo(401);
        SessionStoreTestClient.assertSessionCookieContract(client);
      } finally {
        SessionStoreTestClient.deleteMember(context, member.id());
      }
    }
  }

  @Test
  void unknownSessionStoreFailsStartup() {
    assertThatThrownBy(() -> new SessionStoreConfiguration("disk"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("pawcycle.session.store");
  }
}
