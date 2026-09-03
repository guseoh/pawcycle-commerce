package com.pawcycle.backend.subscription;

import java.math.BigDecimal;

public record PetProjection(long id, String name, String petType, String breed, BigDecimal weightKg) {
  public PetProjection(long id, String name, String petType) {
    this(id, name, petType, null, null);
  }

  public boolean profileComplete() {
    return breed != null && weightKg != null;
  }
}
