package com.pawcycle.backend.subscription.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Feature-local persistence mapping; state transitions remain in the existing use cases. */
@Entity(name = "SubCommandPet")
@Table(name = "pets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id")
  private long memberId;

  private String name;

  @Column(name = "pet_type")
  private String petType;

  private String breed;

  @Column(name = "weight_kg", precision = 5, scale = 2)
  private BigDecimal weightKg;

  public PetEntity(long memberId, String name, String petType) {
    this.memberId = memberId;
    this.name = name;
    this.petType = petType;
  }
}
