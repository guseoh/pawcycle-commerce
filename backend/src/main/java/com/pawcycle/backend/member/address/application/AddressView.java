package com.pawcycle.backend.member.address.application;

/** 회원 배송지 조회 유스케이스의 결과이다. */
public record AddressView(
    long addressId,
    String name,
    String recipientName,
    String recipientPhone,
    String postalCode,
    String addressLine1,
    String addressLine2,
    boolean isDefault) {}
