package com.pawcycle.backend.subscription;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import com.pawcycle.backend.subscription.api.CreatePetRequest;
import com.pawcycle.backend.subscription.api.CreateSubscriptionRequest;
import com.pawcycle.backend.subscription.api.PageResponse;
import com.pawcycle.backend.subscription.api.PetResponse;
import com.pawcycle.backend.subscription.api.PlanVersionResponse;
import com.pawcycle.backend.subscription.api.SubscriptionCommandRequest;
import com.pawcycle.backend.subscription.api.SubscriptionDetailResponse;
import com.pawcycle.backend.subscription.api.SubscriptionSummaryResponse;
import com.pawcycle.backend.subscription.api.UpdatePetRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api")
public class SubscriptionController {

  private final SubscriptionService service;

  public SubscriptionController(SubscriptionService service) {
    this.service = service;
  }

  @PostMapping("/pets")
  ResponseEntity<PetResponse> createPet(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @Valid @RequestBody CreatePetRequest request) {
    return ResponseEntity.status(201).body(service.createPet(principal.memberId(), request));
  }

  @GetMapping("/pets")
  PageResponse<PetResponse> pets(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.pets(principal.memberId(), page, size);
  }

  @GetMapping("/pets/{petId}")
  PetResponse pet(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal, @PathVariable long petId) {
    return service.pet(principal.memberId(), petId);
  }

  @org.springframework.web.bind.annotation.PatchMapping("/pets/{petId}")
  PetResponse updatePet(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long petId,
      @RequestBody UpdatePetRequest request) {
    return service.updatePet(principal.memberId(), petId, request);
  }

  @GetMapping("/subscription-plans")
  PageResponse<PlanVersionResponse> plans(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @RequestParam long petId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.plans(principal.memberId(), petId, page, size);
  }

  @GetMapping("/subscription-plan-versions/{planVersionId}")
  PlanVersionResponse planVersion(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long planVersionId,
      @RequestParam long petId) {
    return service.planVersion(principal.memberId(), petId, planVersionId);
  }

  @PostMapping("/subscriptions")
  ResponseEntity<SubscriptionDetailResponse> createSubscription(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @Valid @RequestBody CreateSubscriptionRequest request) {
    return response(service.createSubscription(principal.memberId(), key, request));
  }

  @GetMapping("/subscriptions")
  PageResponse<SubscriptionSummaryResponse> subscriptions(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.subscriptions(principal.memberId(), page, size);
  }

  @GetMapping("/subscriptions/{subscriptionId}")
  ResponseEntity<SubscriptionDetailResponse> subscription(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long subscriptionId,
      @RequestParam(defaultValue = "0") int schedulePage,
      @RequestParam(defaultValue = "20") int scheduleSize,
      @RequestParam(defaultValue = "0") int commandPage,
      @RequestParam(defaultValue = "20") int commandSize) {
    return response(
        service.subscription(
            principal.memberId(),
            subscriptionId,
            schedulePage,
            scheduleSize,
            commandPage,
            commandSize));
  }

  @PostMapping("/subscriptions/{subscriptionId}/commands/{command}")
  ResponseEntity<SubscriptionDetailResponse> command(
      @AuthenticationPrincipal AuthenticatedMemberPrincipal principal,
      @PathVariable long subscriptionId,
      @PathVariable String command,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestBody(required = false) SubscriptionCommandRequest request) {
    validateIfMatchSyntax(ifMatch);
    return response(
        service.command(
            principal.memberId(),
            subscriptionId,
            command,
            key,
            ifMatch,
            request == null ? SubscriptionCommandRequest.empty() : request));
  }

  private ResponseEntity<SubscriptionDetailResponse> response(SubscriptionResult result) {
    ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status());
    if (result.location() != null) response.header("Location", result.location());
    if (result.etag() != null) response.header("ETag", result.etag());
    if (result.replay()) response.header("Idempotency-Replayed", "true");
    return response.body(result.body());
  }

  private void validateIfMatchSyntax(String value) {
    if (value == null) {
      throw new SubscriptionApiException(428, "IF_MATCH_REQUIRED", "If-Match가 필요합니다.");
    }
    if (!value.matches("\\\"[0-9]+\\\"")) {
      throw new SubscriptionApiException(400, "IF_MATCH_INVALID", "If-Match 형식이 올바르지 않습니다.");
    }
    try {
      Long.parseLong(value.substring(1, value.length() - 1));
    } catch (NumberFormatException exception) {
      throw new SubscriptionApiException(400, "IF_MATCH_INVALID", "If-Match 형식이 올바르지 않습니다.", exception);
    }
  }
}
