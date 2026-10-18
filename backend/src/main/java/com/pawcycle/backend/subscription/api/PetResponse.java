package com.pawcycle.backend.subscription.api;

import com.pawcycle.backend.subscription.model.SubscriptionReadModel;
import java.math.BigDecimal;

public record PetResponse(
    long petId,
    String name,
    String petType,
    String breed,
    BigDecimal weightKg,
    boolean profileComplete) {
  public static PetResponse from(SubscriptionReadModel.Pet view) {
    return new PetResponse(
        view.petId(),
        view.name(),
        view.petType(),
        view.breed(),
        view.weightKg(),
        view.profileComplete());
  }
}
