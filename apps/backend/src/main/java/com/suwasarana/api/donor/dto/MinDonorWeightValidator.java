package com.suwasarana.api.donor.dto;

import com.suwasarana.api.ai.kb.EligibilityFacts;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/** Spring-managed (Spring's validator factory autowires the constructor), so it reads the live {@link EligibilityFacts}. */
public class MinDonorWeightValidator implements ConstraintValidator<MinDonorWeight, BigDecimal> {

    private final EligibilityFacts facts;

    public MinDonorWeightValidator(EligibilityFacts facts) {
        this.facts = facts;
    }

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null || facts.isWeightEligible(value)) return true;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "Weight must be above " + facts.getWeightKgAbove().stripTrailingZeros().toPlainString() + " kg to donate blood")
                .addConstraintViolation();
        return false;
    }
}
