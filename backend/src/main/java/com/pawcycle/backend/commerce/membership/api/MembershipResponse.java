package com.pawcycle.backend.commerce.membership.api;

import com.pawcycle.backend.commerce.membership.model.MembershipView;
import java.math.BigDecimal;
import java.sql.Timestamp;

public record MembershipResponse(
    String code, String name, BigDecimal evaluatedPurchaseAmount, Timestamp evaluatedAt) {
  public static MembershipResponse from(MembershipView view) {
    return new MembershipResponse(
        view.code(),
        view.name(),
        view.evaluatedPurchaseAmount(),
        view.evaluatedAt());
  }
}
