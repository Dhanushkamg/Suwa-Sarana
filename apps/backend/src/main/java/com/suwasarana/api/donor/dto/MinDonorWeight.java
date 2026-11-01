package com.suwasarana.api.donor.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The weight must be strictly above the donor minimum of the knowledge base ({@code EligibilityFacts}, "above 50 kg").
 * A {@code null} weight is valid (unknown). The threshold is read at validation time, so the DTO never hard-codes it.
 */
@Documented
@Constraint(validatedBy = MinDonorWeightValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MinDonorWeight {

    String message() default "Weight must be above the minimum donor weight";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
