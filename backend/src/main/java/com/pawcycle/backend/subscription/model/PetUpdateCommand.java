package com.pawcycle.backend.subscription.model;

import java.math.BigDecimal;

public record PetUpdateCommand(
    String name, boolean namePresent,
    String breed, boolean breedPresent,
    BigDecimal weightKg, boolean weightKgPresent) {
  public boolean hasChanges() {
    return namePresent || breedPresent || weightKgPresent;
  }
}
