package com.pawcycle.backend.member.address.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회원이 저장하는 배송지의 HTTP 요청 계약이다. */
public record MemberAddressRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 100) String recipientName,
    @NotBlank @Size(max = 30) String recipientPhone,
    @NotBlank @Size(max = 20) String postalCode,
    @NotBlank @Size(max = 255) String addressLine1,
    @Size(max = 255) String addressLine2) {}
