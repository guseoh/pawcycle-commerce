package com.pawcycle.backend.commerce;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api")
class CheckoutController {
	private final CommerceService commerce;
	private final CheckoutIdempotencyService checkoutIdempotencyService;
	private final TossPaymentAdapter tossPaymentAdapter;
	CheckoutController(CommerceService commerce, CheckoutIdempotencyService checkoutIdempotencyService, TossPaymentAdapter tossPaymentAdapter) {
		this.commerce = commerce;
		this.checkoutIdempotencyService = checkoutIdempotencyService;
		this.tossPaymentAdapter = tossPaymentAdapter;
	}
	@PostMapping("/checkout") Map<String,Object> checkout(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CommerceRequests.Checkout r) {
		Map<String,Object> result = new LinkedHashMap<>(checkoutIdempotencyService.checkout(p.memberId(),key,r.addressId(),r.memberCouponId(),r.cartVersion()));
		result.put("tossTestEnabled", tossPaymentAdapter.browserTestEnabled());
		return result;
	}
	@PostMapping("/payments/toss/confirm") Map<String,Object> confirm(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@Valid @RequestBody CommerceRequests.Confirm r) { return commerce.confirm(p.memberId(),r.paymentKey(),r.providerOrderId(),r.amount()); }
	@GetMapping("/orders") java.util.List<Map<String,Object>> orders(@AuthenticationPrincipal AuthenticatedMemberPrincipal p) { return commerce.orders(p.memberId()); }
	@GetMapping("/orders/{orderId}") Map<String,Object> order(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long orderId) { return commerce.order(p.memberId(),orderId); }
	@PostMapping("/orders/{orderId}/reorder") Map<String,Object> reorder(@AuthenticationPrincipal AuthenticatedMemberPrincipal p,@PathVariable long orderId,@RequestHeader("Idempotency-Key") String key) { return commerce.reorder(p.memberId(),orderId,key); }
}
