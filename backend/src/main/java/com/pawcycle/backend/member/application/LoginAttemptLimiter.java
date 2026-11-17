package com.pawcycle.backend.member.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-account login failure limit kept in this process's memory (R4).
 *
 * <p>Failures are counted per normalized email whether or not a member exists, so the lock is
 * identical for unknown and existing accounts and reveals nothing about account existence. While
 * an account is locked no credential check runs, including for the correct password. State is not
 * shared between instances and is lost on restart; the edge Nginx per-IP login limit stays the
 * first line of defence.
 */
@Component
public class LoginAttemptLimiter {
  private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

  private final Clock clock;
  private final int maxFailures;
  private final Duration failureWindow;
  private final Duration lockDuration;
  private final int maxTrackedAccounts;
  private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

  public LoginAttemptLimiter(
      Clock clock,
      @Value("${pawcycle.auth.login-throttle.max-failures:10}") int maxFailures,
      @Value("${pawcycle.auth.login-throttle.failure-window:PT15M}") Duration failureWindow,
      @Value("${pawcycle.auth.login-throttle.lock-duration:PT15M}") Duration lockDuration,
      @Value("${pawcycle.auth.login-throttle.max-tracked-accounts:10000}") int maxTrackedAccounts) {
    if (maxFailures < 1
        || maxTrackedAccounts < 1
        || failureWindow.isNegative()
        || failureWindow.isZero()
        || lockDuration.isNegative()
        || lockDuration.isZero()) {
      throw new IllegalArgumentException("invalid login throttle configuration");
    }
    this.clock = clock;
    this.maxFailures = maxFailures;
    this.failureWindow = failureWindow;
    this.lockDuration = lockDuration;
    this.maxTrackedAccounts = maxTrackedAccounts;
  }

  /** Throws when the account is locked; never consults member data. */
  public void checkAllowed(String normalizedEmail) {
    Instant now = clock.instant();
    Attempts current = attempts.get(key(normalizedEmail));
    if (current != null && current.lockedAt(now)) {
      throw new LoginRateLimitedException();
    }
  }

  public void recordFailure(String normalizedEmail) {
    Instant now = clock.instant();
    String key = key(normalizedEmail);
    if (!attempts.containsKey(key) && attempts.size() >= maxTrackedAccounts && !makeRoom(now)) {
      // Every tracked account is locked; keep existing locks rather than evict them.
      log.warn("Login throttle capacity reached; failure not tracked");
      return;
    }
    attempts.compute(key, (ignored, previous) -> next(previous, now));
  }

  public void recordSuccess(String normalizedEmail) {
    attempts.remove(key(normalizedEmail));
  }

  int trackedAccounts() {
    return attempts.size();
  }

  private Attempts next(Attempts previous, Instant now) {
    if (previous == null
        || previous.lockExpiredAt(now)
        || (!previous.locked() && !now.isBefore(previous.windowStart().plus(failureWindow)))) {
      return lockIfExhausted(new Attempts(now, 1, null), now);
    }
    if (previous.locked()) {
      return previous;
    }
    return lockIfExhausted(new Attempts(previous.windowStart(), previous.failures() + 1, null), now);
  }

  private Attempts lockIfExhausted(Attempts candidate, Instant now) {
    return candidate.failures() >= maxFailures
        ? new Attempts(candidate.windowStart(), candidate.failures(), now.plus(lockDuration))
        : candidate;
  }

  /** Drops stale entries first, then unlocked counters; returns whether space was freed. */
  private boolean makeRoom(Instant now) {
    attempts
        .entrySet()
        .removeIf(
            entry ->
                entry.getValue().lockExpiredAt(now)
                    || (!entry.getValue().locked()
                        && !now.isBefore(entry.getValue().windowStart().plus(failureWindow))));
    if (attempts.size() < maxTrackedAccounts) {
      return true;
    }
    attempts.entrySet().removeIf(entry -> !entry.getValue().locked());
    return attempts.size() < maxTrackedAccounts;
  }

  private static String key(String normalizedEmail) {
    // Member lookup is case-insensitive in MySQL, so the local part must not split the budget.
    return normalizedEmail.toLowerCase(Locale.ROOT);
  }

  private record Attempts(Instant windowStart, int failures, Instant lockedUntil) {
    boolean locked() {
      return lockedUntil != null;
    }

    boolean lockedAt(Instant now) {
      return lockedUntil != null && now.isBefore(lockedUntil);
    }

    boolean lockExpiredAt(Instant now) {
      return lockedUntil != null && !now.isBefore(lockedUntil);
    }
  }
}
