package com.pawcycle.backend.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DeliverySchedulePolicyTests {
  @Test void advancesAtLeastOneCycleAndSkipsElapsedCycles() {
    LocalDate date = LocalDate.of(2026, 11, 5);
    assertThat(DeliverySchedulePolicy.firstFutureDate(date, 2, date)).isEqualTo(date.plusWeeks(2));
    assertThat(DeliverySchedulePolicy.firstFutureDate(date, 2, date.plusWeeks(4))).isEqualTo(date.plusWeeks(6));
    assertThat(DeliverySchedulePolicy.firstFutureDate(date, 2, date.minusDays(1))).isEqualTo(date.plusWeeks(2));
  }
}
