package com.pawcycle.backend.member.address.application;

/** 회원 배송지 생성·수정 유스케이스의 입력이다. */
public record MemberAddressCommand(
    String name,
    String recipientName,
    String recipientPhone,
    String postalCode,
    String addressLine1,
    String addressLine2) {}
