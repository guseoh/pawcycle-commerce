package com.pawcycle.backend.subscription.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = DeliveryCycleValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedDeliveryCycle {

	String message() default "배송 주기는 2주, 4주 또는 8주로 선택해 주세요.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
