package com.pawcycle.backend.subscription.domain;

import java.time.LocalDate;

/** Existing next-cycle arithmetic, independent of schedule lookup, locking and persistence. */
public final class DeliverySchedulePolicy {
  private DeliverySchedulePolicy() {}

  public static LocalDate firstFutureDate(LocalDate scheduledDate, int cycleWeeks, LocalDate today) {
    LocalDate next = scheduledDate;
    do {
      next = next.plusWeeks(cycleWeeks);
    } while (!next.isAfter(today));
    return next;
  }
}
