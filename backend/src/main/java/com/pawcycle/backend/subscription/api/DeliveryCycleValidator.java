package com.pawcycle.backend.subscription.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

public final class DeliveryCycleValidator implements ConstraintValidator<AllowedDeliveryCycle, Integer> {

	private static final Set<Integer> ALLOWED_CYCLES = Set.of(2, 4, 8);

	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		return value == null || ALLOWED_CYCLES.contains(value);
	}
}
