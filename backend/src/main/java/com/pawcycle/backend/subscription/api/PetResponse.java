package com.pawcycle.backend.subscription.api;

import java.math.BigDecimal;

public record PetResponse(
    long petId,
    String name,
    String petType,
    String breed,
    BigDecimal weightKg,
    boolean profileComplete) {}
