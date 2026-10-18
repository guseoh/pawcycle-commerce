package com.pawcycle.backend.commerce.membership.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record MembershipView(
    String code, String name, BigDecimal evaluatedPurchaseAmount, Timestamp evaluatedAt) {}
