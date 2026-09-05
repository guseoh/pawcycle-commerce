package com.pawcycle.backend.commerce.order.api;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record OrderSummaryResponse(
    long orderId,
    String orderNumber,
    String source,
    String status,
    BigDecimal paymentAmount,
    Timestamp createdAt,
    Timestamp paidAt) {}
