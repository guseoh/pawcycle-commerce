package com.pawcycle.backend.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SubscriptionFailureTests {
  @ParameterizedTest
  @EnumSource(SubscriptionFailure.Kind.class)
  void preservesKindCodeMessageAndCauseWithBothConstructors(SubscriptionFailure.Kind kind) {
    var cause = new NumberFormatException("original cause");
    var failure = new SubscriptionFailure(kind, "existing-code", "기존 메시지", cause);
    assertThat(failure).isInstanceOf(RuntimeException.class).hasMessage("기존 메시지").hasCause(cause);
    assertThat(failure.kind()).isSameAs(kind);
    assertThat(failure.code()).isEqualTo("existing-code");
    var withoutCause = new SubscriptionFailure(kind, failure.code(), failure.getMessage());
    assertThat(withoutCause.kind()).isSameAs(kind);
    assertThat(withoutCause.code()).isEqualTo(failure.code());
    assertThat(withoutCause).hasMessage(failure.getMessage()).hasNoCause();
  }
}
