package com.pawcycle.backend.commerce.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.pawcycle.backend.common.scheduling.ScheduledJobRunner;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MembershipReevaluationBackoffTests {
  @Test
  void backoffDoublesFromTenSecondsAndCapsAtOneHour() {
    assertThat(MembershipReevaluationService.backoff(0)).isEqualTo(Duration.ofSeconds(10));
    assertThat(MembershipReevaluationService.backoff(1)).isEqualTo(Duration.ofSeconds(20));
    assertThat(MembershipReevaluationService.backoff(5)).isEqualTo(Duration.ofSeconds(320));
    assertThat(MembershipReevaluationService.backoff(9)).isEqualTo(Duration.ofHours(1));
    assertThat(MembershipReevaluationService.backoff(1_000)).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void triggerRefusesANonPositiveBatch() {
    assertThatThrownBy(() -> new MembershipReevaluationTrigger(
            mock(MembershipReevaluationService.class), mock(ScheduledJobRunner.class), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
