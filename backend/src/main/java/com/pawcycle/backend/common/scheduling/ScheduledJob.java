package com.pawcycle.backend.common.scheduling;

/** Every {@code @Scheduled} job of the application. Fixed values keep the {@code job} tag bounded. */
public enum ScheduledJob {
  CHECKOUT_EXPIRATION("checkout-expiration"),
  SUBSCRIPTION_BILLING("subscription-billing"),
  SUBSCRIPTION_RECONCILIATION("subscription-reconciliation"),
  SUBSCRIPTION_ORDER_AUTOMATION("subscription-order-automation"),
  SUBSCRIPTION_DELIVERY_REMINDER("subscription-delivery-reminder"),
  COMMERCE_METRICS_REFRESH("commerce-metrics-refresh"),
  IDEMPOTENCY_METRICS_REFRESH("idempotency-metrics-refresh"),
  SUBSCRIPTION_BACKLOG_METRICS_REFRESH("subscription-backlog-metrics-refresh"),
  IDEMPOTENCY_CLEANUP("idempotency-cleanup"),
  MEMBERSHIP_REEVALUATION("membership-reevaluation");

  private final String tag;

  ScheduledJob(String tag) {
    this.tag = tag;
  }

  public String tag() {
    return tag;
  }
}
