package com.pawcycle.backend.subscription.domain;

import java.time.LocalDate;

/** Shared command/detail sale condition; SQL filtering and pet/cycle validation stay at their boundaries. */
public final class PlanSalePolicy {
  private PlanSalePolicy() {}

  public static boolean available(long versionId, Long currentVersionId, String planName,
      boolean onSale, boolean migrationOnly, LocalDate startsOn, LocalDate endsOn, LocalDate today) {
    return currentVersionId != null && currentVersionId == versionId && planName != null
        && onSale && !migrationOnly
        && (startsOn == null || !startsOn.isAfter(today))
        && (endsOn == null || !endsOn.isBefore(today));
  }
}
