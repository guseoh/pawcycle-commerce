package com.pawcycle.backend.commerce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

class TossTestPaymentAdapterContextTests {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestContextConfiguration.class)
          .withPropertyValues(
              "spring.profiles.active=local-integration",
              "pawcycle.toss.test.enabled=${PAWCYCLE_TOSS_TEST_ENABLED:false}",
              "pawcycle.toss.test.secret-key=${PAWCYCLE_TOSS_TEST_SECRET_KEY:}",
              "PAWCYCLE_TOSS_TEST_ENABLED=true",
              "PAWCYCLE_TOSS_TEST_SECRET_KEY=test_sk_context");

  @Test
  void createsTossTestAdapterWhenEnvironmentOptInIsEnabled() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(TossPaymentAdapter.class);
          assertThat(context.getBean(TossPaymentAdapter.class))
              .isInstanceOf(TossTestPaymentAdapter.class);
        });
  }

  @TestConfiguration(proxyBeanMethods = false)
  @Import(TossTestPaymentAdapter.class)
  static class TestContextConfiguration {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
