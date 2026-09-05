package com.pawcycle.backend.commerce;

import com.pawcycle.backend.commerce.checkout.api.CheckoutResponse;
import com.pawcycle.backend.commerce.checkout.application.CheckoutApplicationService;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade for callers that historically addressed checkout idempotency separately.
 * The transaction and idempotency boundary now live in {@link CheckoutApplicationService}.
 */
@Service
public class CheckoutIdempotencyService {

    private final CheckoutApplicationService checkoutApplicationService;

    public CheckoutIdempotencyService(CheckoutApplicationService checkoutApplicationService) {
        this.checkoutApplicationService = checkoutApplicationService;
    }

    public CheckoutResponse checkout(
            long memberId,
            String idempotencyKey,
            long addressId,
            Long couponId,
            Long requestedCartVersion
    ) {
        return checkoutApplicationService.checkout(
                memberId, idempotencyKey, addressId, couponId, requestedCartVersion);
    }

    public CheckoutResponse checkout(
            long memberId,
            String idempotencyKey,
            long addressId,
            Long couponId
    ) {
        return checkoutApplicationService.checkout(memberId, idempotencyKey, addressId, couponId, null);
    }
}
