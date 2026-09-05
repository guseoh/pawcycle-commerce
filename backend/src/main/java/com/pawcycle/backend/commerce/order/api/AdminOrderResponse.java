package com.pawcycle.backend.commerce.order.api;

import java.math.BigDecimal;
import java.sql.Timestamp;

public record AdminOrderResponse(
    long orderId,
    String orderNumber,
    long memberId,
    String status,
    BigDecimal paymentAmount,
    Timestamp createdAt) {}
