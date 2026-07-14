package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.subscription.application.SubscriptionApplicationService;
import com.pawcycle.backend.subscription.application.SubscriptionCreateResult;
import com.pawcycle.backend.subscription.application.SubscriptionDetailView;
import com.pawcycle.backend.subscription.application.SubscriptionListView;
import com.pawcycle.backend.subscription.application.SubscriptionNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

	private final SubscriptionApplicationService subscriptionApplicationService;

	public SubscriptionController(SubscriptionApplicationService subscriptionApplicationService) {
		this.subscriptionApplicationService = subscriptionApplicationService;
	}

	@PostMapping
	ResponseEntity<SubscriptionCreateResult> create(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@Valid @RequestBody SubscriptionCreateRequest request) {
		SubscriptionCreateResult result = subscriptionApplicationService.create(
				principal.memberId(), request.skuId(), request.quantity(), request.deliveryCycleWeeks());
		return ResponseEntity.status(HttpStatus.CREATED).body(result);
	}

	@GetMapping
	SubscriptionListView subscriptions(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal) {
		return subscriptionApplicationService.findOwnedSubscriptions(principal.memberId());
	}

	@GetMapping("/{subscriptionId}")
	SubscriptionDetailView subscription(
			@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable String subscriptionId) {
		try {
			return subscriptionApplicationService.findOwnedSubscription(
					principal.memberId(), Long.valueOf(subscriptionId));
		} catch (NumberFormatException exception) {
			throw new SubscriptionNotFoundException();
		}
	}
}
