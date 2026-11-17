package com.pawcycle.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTests {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T00:00:00Z"));

  private LoginAttemptLimiter limiter(int maxTracked) {
    return new LoginAttemptLimiter(
        clock, 3, Duration.ofMinutes(15), Duration.ofMinutes(10), maxTracked);
  }

  @Test
  void locksAfterMaxFailuresAndUnlocksWhenLockExpires() {
    LoginAttemptLimiter limiter = limiter(100);
    for (int failure = 0; failure < 3; failure++) {
      assertThatCode(() -> limiter.checkAllowed("member@example.com")).doesNotThrowAnyException();
      limiter.recordFailure("member@example.com");
    }

    assertThatThrownBy(() -> limiter.checkAllowed("member@example.com"))
        .isInstanceOf(LoginRateLimitedException.class);
    clock.advance(Duration.ofMinutes(10).minusSeconds(1));
    assertThatThrownBy(() -> limiter.checkAllowed("member@example.com"))
        .isInstanceOf(LoginRateLimitedException.class);

    clock.advance(Duration.ofSeconds(1));
    assertThatCode(() -> limiter.checkAllowed("member@example.com")).doesNotThrowAnyException();
    // A fresh budget starts after the lock: one more failure does not relock.
    limiter.recordFailure("member@example.com");
    assertThatCode(() -> limiter.checkAllowed("member@example.com")).doesNotThrowAnyException();
  }

  @Test
  void failuresOutsideTheWindowStartANewCount() {
    LoginAttemptLimiter limiter = limiter(100);
    limiter.recordFailure("member@example.com");
    limiter.recordFailure("member@example.com");
    clock.advance(Duration.ofMinutes(15));
    limiter.recordFailure("member@example.com");

    assertThatCode(() -> limiter.checkAllowed("member@example.com")).doesNotThrowAnyException();
  }

  @Test
  void successClearsFailuresAndAccountsAreIndependent() {
    LoginAttemptLimiter limiter = limiter(100);
    limiter.recordFailure("member@example.com");
    limiter.recordFailure("member@example.com");
    limiter.recordSuccess("member@example.com");
    limiter.recordFailure("member@example.com");
    limiter.recordFailure("member@example.com");
    assertThatCode(() -> limiter.checkAllowed("member@example.com")).doesNotThrowAnyException();

    for (int failure = 0; failure < 3; failure++) {
      limiter.recordFailure("victim@example.com");
    }
    assertThatThrownBy(() -> limiter.checkAllowed("victim@example.com"))
        .isInstanceOf(LoginRateLimitedException.class);
    assertThatCode(() -> limiter.checkAllowed("member@example.com")).doesNotThrowAnyException();
  }

  @Test
  void localPartCaseDoesNotSplitTheBudget() {
    LoginAttemptLimiter limiter = limiter(100);
    limiter.recordFailure("Member@example.com");
    limiter.recordFailure("MEMBER@example.com");
    limiter.recordFailure("member@example.com");

    assertThatThrownBy(() -> limiter.checkAllowed("mEmBeR@example.com"))
        .isInstanceOf(LoginRateLimitedException.class);
  }

  @Test
  void capacityEvictsUnlockedCountersButKeepsLocks() {
    LoginAttemptLimiter limiter = limiter(2);
    for (int failure = 0; failure < 3; failure++) {
      limiter.recordFailure("locked@example.com");
    }
    limiter.recordFailure("counting@example.com");
    limiter.recordFailure("new@example.com");

    assertThat(limiter.trackedAccounts()).isEqualTo(2);
    assertThatThrownBy(() -> limiter.checkAllowed("locked@example.com"))
        .isInstanceOf(LoginRateLimitedException.class);

    for (int failure = 0; failure < 3; failure++) {
      limiter.recordFailure("new@example.com");
    }
    limiter.recordFailure("overflow@example.com");
    assertThat(limiter.trackedAccounts()).isEqualTo(2);
    assertThatThrownBy(() -> limiter.checkAllowed("new@example.com"))
        .isInstanceOf(LoginRateLimitedException.class);
  }

  @Test
  void concurrentFailuresAreAllCounted() throws Exception {
    LoginAttemptLimiter limiter =
        new LoginAttemptLimiter(clock, 64, Duration.ofMinutes(15), Duration.ofMinutes(10), 100);
    ExecutorService executor = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int failure = 0; failure < 63; failure++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  limiter.recordFailure("member@example.com");
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThatCode(() -> limiter.checkAllowed("member@example.com")).doesNotThrowAnyException();
    limiter.recordFailure("member@example.com");
    assertThatThrownBy(() -> limiter.checkAllowed("member@example.com"))
        .isInstanceOf(LoginRateLimitedException.class);
  }

  private static final class MutableClock extends Clock {
    private volatile Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
