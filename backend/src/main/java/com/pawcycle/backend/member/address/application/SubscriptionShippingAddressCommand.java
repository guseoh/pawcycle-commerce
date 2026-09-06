package com.pawcycle.backend.member.address.application;

/** 구독 배송지 snapshot 변경 유스케이스의 입력이다. */
public record SubscriptionShippingAddressCommand(
    String recipientName,
    String recipientPhone,
    String postalCode,
    String addressLine1,
    String addressLine2) {}
