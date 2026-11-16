package com.pawcycle.backend.common.external;

/** Every external provider call whose failure is recorded. Fixed values keep metric tags bounded. */
public enum ExternalCall {
  TOSS_PAYMENT_CONFIRM("toss", "payment.confirm"),
  TOSS_PAYMENT_QUERY("toss", "payment.query"),
  TOSS_REFUND("toss", "refund"),
  TOSS_REFUND_QUERY("toss", "refund.query"),
  TOSS_BILLING_CHARGE("toss", "billing.charge"),
  TOSS_BILLING_QUERY("toss", "billing.query"),
  OPENAI_RECOMMENDATION("openai", "recommendation"),
  OPENAI_REVIEW_SUMMARY("openai", "review.summary"),
  OPENAI_PRODUCT_COMPARISON("openai", "product.comparison");

  private final String provider;
  private final String operation;

  ExternalCall(String provider, String operation) {
    this.provider = provider;
    this.operation = operation;
  }

  public String provider() {
    return provider;
  }

  public String operation() {
    return operation;
  }
}
