package com.pawcycle.backend.subscription;

import com.pawcycle.backend.subscription.api.CreatePetRequest;
import com.pawcycle.backend.subscription.api.CreateSubscriptionRequest;
import com.pawcycle.backend.subscription.api.PageResponse;
import com.pawcycle.backend.subscription.api.PetResponse;
import com.pawcycle.backend.subscription.api.PlanVersionResponse;
import com.pawcycle.backend.subscription.api.SubscriptionCommandRequest;
import com.pawcycle.backend.subscription.api.SubscriptionDetailResponse;
import com.pawcycle.backend.subscription.api.SubscriptionSummaryResponse;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade for in-process callers. Use-case services own transaction boundaries; this class
 * contains no SQL, persistence mapping, or HTTP response construction.
 */
@Service
public class SubscriptionService {
  private final PetPlanApplicationService petPlans;
  private final SubscriptionCreationApplicationService creations;
  private final SubscriptionQueryApplicationService queries;
  private final SubscriptionCommandApplicationService commands;
  private final SubscriptionReconciliationApplicationService reconciliation;

  public SubscriptionService(
      PetPlanApplicationService petPlans,
      SubscriptionCreationApplicationService creations,
      SubscriptionQueryApplicationService queries,
      SubscriptionCommandApplicationService commands,
      SubscriptionReconciliationApplicationService reconciliation) {
    this.petPlans = petPlans;
    this.creations = creations;
    this.queries = queries;
    this.commands = commands;
    this.reconciliation = reconciliation;
  }

  public PetResponse createPet(long memberId, CreatePetRequest request) {
    return petPlans.createPet(memberId, request);
  }

  public PetResponse updatePet(long memberId, long petId, com.pawcycle.backend.subscription.api.UpdatePetRequest request) {
    return petPlans.updatePet(memberId, petId, request);
  }

  public PageResponse<PetResponse> pets(long memberId, int page, int size) {
    return petPlans.pets(memberId, page, size);
  }

  public PetResponse pet(long memberId, long petId) {
    return petPlans.pet(memberId, petId);
  }

  public PageResponse<PlanVersionResponse> plans(long memberId, long petId, int page, int size) {
    return petPlans.plans(memberId, petId, page, size);
  }

  public PlanVersionResponse planVersion(long memberId, long petId, long versionId) {
    return petPlans.planVersion(memberId, petId, versionId);
  }

  public SubscriptionResult createSubscription(
      long memberId, String key, CreateSubscriptionRequest request) {
    return result(creations.create(memberId, key, request));
  }

  public PageResponse<SubscriptionSummaryResponse> subscriptions(long memberId, int page, int size) {
    return queries.subscriptions(memberId, page, size);
  }

  public SubscriptionResult subscription(
      long memberId,
      long subscriptionId,
      int schedulePage,
      int scheduleSize,
      int commandPage,
      int commandSize) {
    return result(
        queries.subscription(
            memberId, subscriptionId, schedulePage, scheduleSize, commandPage, commandSize));
  }

  public SubscriptionResult command(
      long memberId,
      long subscriptionId,
      String command,
      String key,
      String ifMatch,
      SubscriptionCommandRequest request) {
    return result(commands.command(memberId, subscriptionId, command, key, ifMatch, request));
  }

  public void reconcileActiveSubscriptions() {
    reconciliation.reconcileActiveSubscriptions();
  }

  private SubscriptionResult result(SubscriptionOperationResult result) {
    return new SubscriptionResult(
        result.status(), result.body(), result.location(), result.etag(), result.replay());
  }

}
