package com.pawcycle.backend.recommendation;

/** Explainable MVP ranking weights. Keep product and content signals in one policy. */
final class RecommendationPolicy {
  static final int MAX_REPEATED_SIGNAL = 3;
  static final int PURCHASE_PRODUCT = 40;
  static final int PURCHASE_CATEGORY = 20;
  static final int PURCHASE_BRAND = 8;
  static final int PURCHASE_FACET = 4;
  static final int WISHLIST_PRODUCT = 30;
  static final int WISHLIST_CATEGORY = 12;
  static final int WISHLIST_BRAND = 6;
  static final int WISHLIST_FACET = 3;
  static final int CLICK_PRODUCT = 15;
  static final int CLICK_CATEGORY = 8;
  static final int CLICK_BRAND = 4;
  static final int CLICK_FACET = 2;
  static final int VIEW_PRODUCT = 6;
  static final int VIEW_CATEGORY = 3;
  static final int VIEW_BRAND = 2;
  static final int VIEW_FACET = 1;
  static final int SUBSCRIPTION_CATEGORY = 30;
  static final int SUBSCRIPTION_BRAND = 10;
  static final int SUBSCRIPTION_FACET = 5;
  static final int FILTER_CATEGORY = 3;
  static final int FILTER_BRAND = 2;
  static final int FILTER_FACET = 1;

  private RecommendationPolicy() {}

  static int cap(int value) {
    return Math.min(MAX_REPEATED_SIGNAL, Math.max(0, value));
  }
}
