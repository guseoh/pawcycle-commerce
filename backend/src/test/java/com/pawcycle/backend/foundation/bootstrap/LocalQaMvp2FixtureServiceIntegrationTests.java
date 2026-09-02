package com.pawcycle.backend.foundation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocalQaMvp2FixtureServiceIntegrationTests {

  @Autowired private LocalQaBootstrapService bootstrapService;
  @Autowired private LocalQaMvp2FixtureService fixtureService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void firstAndRepeatedRunCreateOneExactSellablePlanFixture() {
    bootstrapService.bootstrap(runtimeQaEmail(), UUID.randomUUID().toString(), false);

    fixtureService.bootstrap();
    fixtureService.bootstrap();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscription_plans WHERE name = ?",
                Integer.class,
                LocalQaMvp2FixtureService.PLAN_NAME))
        .isEqualTo(1);
    Long planVersionId =
        jdbcTemplate.queryForObject(
            """
            SELECT current_plan_version_id
            FROM subscription_plans
            WHERE name = ?
            """,
            Long.class,
            LocalQaMvp2FixtureService.PLAN_NAME);
    assertThat(planVersionId).isNotNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT package_price_krw FROM plan_versions WHERE id = ?",
                Long.class,
                planVersionId))
        .isEqualTo(LocalQaMvp2FixtureService.PACKAGE_PRICE_KRW);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plan_items WHERE plan_version_id = ? AND quantity = 1",
                Integer.class,
                planVersionId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForList(
                """
                SELECT delivery_cycle_weeks
                FROM plan_version_delivery_cycles
                WHERE plan_version_id = ?
                ORDER BY delivery_cycle_weeks
                """,
                Integer.class,
                planVersionId))
        .containsExactlyElementsOf(LocalQaMvp2FixtureService.DELIVERY_CYCLES);
  }

  @Test
  void mismatchedExistingPlanFailsWithoutCreatingAnotherPlan() {
    bootstrapService.bootstrap(runtimeQaEmail(), UUID.randomUUID().toString(), false);
    fixtureService.bootstrap();
    Long planVersionId =
        jdbcTemplate.queryForObject(
            "SELECT current_plan_version_id FROM subscription_plans WHERE name = ?",
            Long.class,
            LocalQaMvp2FixtureService.PLAN_NAME);
    jdbcTemplate.update(
        "UPDATE plan_versions SET package_price_krw = package_price_krw + 1 WHERE id = ?",
        planVersionId);

    assertThatThrownBy(fixtureService::bootstrap)
        .isInstanceOf(LocalQaBootstrapException.class)
        .hasMessage("로컬 QA MVP2 Plan fixture가 기존 데이터와 충돌합니다.");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscription_plans WHERE name = ?",
                Integer.class,
                LocalQaMvp2FixtureService.PLAN_NAME))
        .isEqualTo(1);
  }

  private String runtimeQaEmail() {
    return LocalQaBootstrapService.QA_EMAIL_LOCAL_PART + "@" + UUID.randomUUID() + ".example";
  }
}
