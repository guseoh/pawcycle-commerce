package com.pawcycle.backend.subscription.domain;

import java.util.Locale;

public enum SubscriptionCommandType {
  CHANGE_PLAN, CHANGE_DELIVERY_CYCLE, RESCHEDULE_NEXT, SKIP_NEXT, PAUSE, RESUME, CANCEL,
  SET_NEXT_DELIVERY_ADDON, REMOVE_NEXT_DELIVERY_ADDON;

  public static SubscriptionCommandType fromExternalValue(String command) {
    String normalized = command.toUpperCase(Locale.ROOT).replace('-', '_');
    try {
      return valueOf(normalized);
    } catch (IllegalArgumentException exception) {
      throw new SubscriptionFailure(SubscriptionFailure.Kind.NOT_FOUND,
          "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다.");
    }
  }
}
