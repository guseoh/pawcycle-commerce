package com.pawcycle.backend.subscription.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.subscription.domain.SubscriptionFailure;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SubscriptionApplicationSupportFailureTests {
  private final SubscriptionApplicationSupport support = new SubscriptionApplicationSupport(new ObjectMapper(), Clock.systemUTC());

  @Test
  void missingAndMalformedPreconditionsKeepDistinctFailures() {
    assertThatThrownBy(() -> support.parseEtag(null)).isInstanceOfSatisfying(SubscriptionFailure.class, failure -> {
      assertThat(failure.kind()).isEqualTo(SubscriptionFailure.Kind.PRECONDITION_REQUIRED);
      assertThat(failure.code()).isEqualTo("IF_MATCH_REQUIRED");
      assertThat(failure).hasMessage("If-Match가 필요합니다.").hasNoCause();
    });
    assertThatThrownBy(() -> support.parseEtag("invalid")).isInstanceOfSatisfying(SubscriptionFailure.class, failure -> {
      assertThat(failure.kind()).isEqualTo(SubscriptionFailure.Kind.BAD_REQUEST);
      assertThat(failure.code()).isEqualTo("IF_MATCH_INVALID");
      assertThat(failure).hasMessage("If-Match 형식이 올바르지 않습니다.").hasNoCause();
    });
  }

  @Test
  void overflowKeepsItsNumberFormatCauseAndValidVersionIsUnchanged() {
    assertThatThrownBy(() -> support.parseEtag("\"999999999999999999999999\""))
        .isInstanceOfSatisfying(SubscriptionFailure.class, failure -> {
          assertThat(failure.kind()).isEqualTo(SubscriptionFailure.Kind.BAD_REQUEST);
          assertThat(failure.code()).isEqualTo("IF_MATCH_INVALID");
          assertThat(failure).hasMessage("If-Match 형식이 올바르지 않습니다.").hasCauseInstanceOf(NumberFormatException.class);
        });
    assertThat(support.parseEtag("\"9223372036854775807\"")).isEqualTo(Long.MAX_VALUE);
  }
}
