package com.pawcycle.backend.member.address.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 구독 배송지 변경에 필요한 수령인 정보의 HTTP 요청 계약이다. */
public record SubscriptionShippingAddressRequest(
    @Size(max = 100) String name,
    @NotBlank @Size(max = 100) String recipientName,
    @NotBlank @Size(max = 30) String recipientPhone,
    @NotBlank @Size(max = 20) String postalCode,
    @NotBlank @Size(max = 255) String addressLine1,
    @Size(max = 255) String addressLine2) {}
