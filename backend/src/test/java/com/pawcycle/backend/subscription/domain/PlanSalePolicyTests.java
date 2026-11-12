package com.pawcycle.backend.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlanSalePolicyTests {
  @Test void saleDatesAreInclusiveAndNullBoundsAreAllowed() {
    LocalDate today = LocalDate.of(2026, 11, 5);
    assertThat(PlanSalePolicy.available(1, 1L, "plan", true, false, today, today, today)).isTrue();
    assertThat(PlanSalePolicy.available(1, 1L, "plan", true, false, null, null, today)).isTrue();
    assertThat(PlanSalePolicy.available(1, 1L, "plan", true, false, today.plusDays(1), null, today)).isFalse();
    assertThat(PlanSalePolicy.available(1, 1L, "plan", true, false, null, today.minusDays(1), today)).isFalse();
    assertThat(PlanSalePolicy.available(1, null, "plan", true, false, null, null, today)).isFalse();
    assertThat(PlanSalePolicy.available(1, 2L, "plan", true, false, null, null, today)).isFalse();
    assertThat(PlanSalePolicy.available(1, 1L, null, true, false, null, null, today)).isFalse();
    assertThat(PlanSalePolicy.available(1, 1L, "plan", false, false, null, null, today)).isFalse();
    assertThat(PlanSalePolicy.available(1, 1L, "plan", true, true, null, null, today)).isFalse();
  }
}
