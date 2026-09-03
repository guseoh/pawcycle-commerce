package com.pawcycle.backend.subscription.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public final class UpdatePetRequest {
  private String name;
  private String breed;
  private BigDecimal weightKg;
  private boolean namePresent;
  private boolean breedPresent;
  private boolean weightKgPresent;

  @JsonSetter("name")
  public void readName(String value) {
    name = value;
    namePresent = true;
  }

  @JsonSetter("breed")
  public void readBreed(String value) {
    breed = value;
    breedPresent = true;
  }

  @JsonSetter("weightKg")
  public void readWeightKg(BigDecimal value) {
    weightKg = value;
    weightKgPresent = true;
  }

  public boolean hasChanges() {
    return namePresent || breedPresent || weightKgPresent;
  }
}
