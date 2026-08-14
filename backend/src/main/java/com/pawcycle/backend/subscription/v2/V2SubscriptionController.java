package com.pawcycle.backend.subscription.v2;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2")
@ConditionalOnProperty(prefix = "pawcycle.mvp2.reconciliation", name = "enabled", havingValue = "true")
public class V2SubscriptionController {

	private final V2SubscriptionService service;

	public V2SubscriptionController(V2SubscriptionService service) { this.service = service; }

	@PostMapping("/pets")
	ResponseEntity<Map<String, Object>> createPet(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@RequestBody Map<String, Object> body) {
		return ResponseEntity.status(201).body(service.createPet(principal.memberId(), body));
	}

	@GetMapping("/pets")
	Map<String, Object> pets(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.pets(principal.memberId(), page, size);
	}

	@GetMapping("/pets/{petId}")
	Map<String, Object> pet(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long petId) {
		return service.pet(principal.memberId(), petId);
	}

	@GetMapping("/subscription-plans")
	Map<String, Object> plans(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @RequestParam long petId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.plans(principal.memberId(), petId, page, size);
	}

	@GetMapping("/subscription-plan-versions/{planVersionId}")
	Map<String, Object> planVersion(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable long planVersionId, @RequestParam long petId) {
		return service.planVersion(principal.memberId(), petId, planVersionId);
	}

	@PostMapping("/subscriptions")
	ResponseEntity<Map<String, Object>> createSubscription(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody Map<String, Object> body) {
		return response(service.createSubscription(principal.memberId(), key, body));
	}

	@GetMapping("/subscriptions")
	Map<String, Object> subscriptions(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.subscriptions(principal.memberId(), page, size);
	}

	@GetMapping("/subscriptions/{subscriptionId}")
	ResponseEntity<Map<String, Object>> subscription(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable long subscriptionId, @RequestParam(defaultValue = "0") int schedulePage,
			@RequestParam(defaultValue = "20") int scheduleSize, @RequestParam(defaultValue = "0") int commandPage,
			@RequestParam(defaultValue = "20") int commandSize) {
		return response(service.subscription(principal.memberId(), subscriptionId, schedulePage, scheduleSize, commandPage, commandSize));
	}

	@PostMapping("/subscriptions/{subscriptionId}/commands/{command}")
	ResponseEntity<Map<String, Object>> command(@AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
			@PathVariable long subscriptionId, @PathVariable String command,
			@RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestHeader(value = "If-Match", required = false) String ifMatch,
			@RequestBody(required = false) Map<String, Object> body) {
		validateIfMatchSyntax(ifMatch);
		return response(service.command(principal.memberId(), subscriptionId, command, key, ifMatch, body == null ? Map.of() : body));
	}

	private ResponseEntity<Map<String, Object>> response(V2SubscriptionService.V2Result result) {
		ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status());
		if (result.location() != null) response.header("Location", result.location());
		if (result.etag() != null) response.header("ETag", result.etag());
		if (result.replay()) response.header("Idempotency-Replayed", "true");
		return response.body(result.body());
	}

	private void validateIfMatchSyntax(String value) {
		if (value == null) {
			throw new V2ApiException(428, "IF_MATCH_REQUIRED", "If-Match가 필요합니다.");
		}
		if (!value.matches("\\\"[0-9]+\\\"")) {
			throw new V2ApiException(400, "IF_MATCH_INVALID", "If-Match 형식이 올바르지 않습니다.");
		}
		try {
			Long.parseLong(value.substring(1, value.length() - 1));
		} catch (NumberFormatException exception) {
			throw new V2ApiException(400, "IF_MATCH_INVALID", "If-Match 형식이 올바르지 않습니다.", exception);
		}
	}
}
