package com.pawcycle.backend.commerce.membership.api;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record MembershipResponse(
    String code, String name, BigDecimal evaluatedPurchaseAmount, Timestamp evaluatedAt) {}
