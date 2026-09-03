package com.pawcycle.backend.subscription.api;

import jakarta.validation.constraints.NotBlank;

public record CreatePetRequest(@NotBlank String name, @NotBlank String petType) {}
