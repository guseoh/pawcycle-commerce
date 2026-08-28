package com.pawcycle.backend.subscription.v2;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class RepeatCommerceController {
	private final RepeatCommerceService service;
	RepeatCommerceController(RepeatCommerceService service){this.service=service;}
	@GetMapping("/recommendations/reorder-timing") Map<String,Object> reorder(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal){return service.reorderTiming(principal.memberId());}
	@GetMapping("/v2/subscriptions/{subscriptionId}/cycle-suggestion") Map<String,Object> cycle(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long subscriptionId){return service.cycleSuggestion(principal.memberId(),subscriptionId);}
	@GetMapping("/orders/{orderId}/subscription-options") Map<String,Object> options(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,@PathVariable long orderId){return service.subscriptionOptions(principal.memberId(),orderId);}
}
