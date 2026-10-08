package com.pawcycle.backend.subscription.persistence.projection;

import java.math.BigDecimal;
import org.springframework.data.annotation.PersistenceCreator;

public record PetProjection(long id, String name, String petType, String breed, BigDecimal weightKg) {
  @PersistenceCreator
  public PetProjection {}

  public PetProjection(long id, String name, String petType) {
    this(id, name, petType, null, null);
  }

  public boolean profileComplete() {
    return breed != null && weightKg != null;
  }
}
