package com.pawcycle.backend.subscription.application;

import com.pawcycle.backend.subscription.model.SubscriptionCreateCommand;
import com.pawcycle.backend.subscription.model.SubscriptionCommand;
import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import com.pawcycle.backend.subscription.model.PetCreateCommand;
import com.pawcycle.backend.subscription.model.PetUpdateCommand;
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

  public SubscriptionReadModel.Pet createPet(long memberId, PetCreateCommand request) {
    return petPlans.createPet(memberId, request);
  }

  public SubscriptionReadModel.Pet updatePet(long memberId, long petId, PetUpdateCommand request) {
    return petPlans.updatePet(memberId, petId, request);
  }

  public SubscriptionReadModel.Page<SubscriptionReadModel.Pet> pets(long memberId, int page, int size) {
    return petPlans.pets(memberId, page, size);
  }

  public SubscriptionReadModel.Pet pet(long memberId, long petId) {
    return petPlans.pet(memberId, petId);
  }

  public SubscriptionReadModel.Page<SubscriptionReadModel.PlanVersion> plans(long memberId, long petId, int page, int size) {
    return petPlans.plans(memberId, petId, page, size);
  }

  public SubscriptionReadModel.PlanVersion planVersion(long memberId, long petId, long versionId) {
    return petPlans.planVersion(memberId, petId, versionId);
  }

  public SubscriptionResult createSubscription(
      long memberId, String key, SubscriptionCreateCommand request) {
    return result(creations.create(memberId, key, request));
  }

  public SubscriptionReadModel.Page<SubscriptionReadModel.Summary> subscriptions(long memberId, int page, int size) {
    return queries.subscriptions(memberId, page, size);
  }

  public SubscriptionReadModel.Detail subscription(
      long memberId,
      long subscriptionId,
      int schedulePage,
      int scheduleSize,
      int commandPage,
      int commandSize) {
    return queries.subscription(
        memberId, subscriptionId, schedulePage, scheduleSize, commandPage, commandSize);
  }

  public SubscriptionResult command(
      long memberId,
      long subscriptionId,
      String command,
      String key,
      String ifMatch,
      SubscriptionCommand request) {
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
