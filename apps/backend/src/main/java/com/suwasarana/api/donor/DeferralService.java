package com.suwasarana.api.donor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeferralService {

    private final DeferralRepository deferralRepository;

    public DeferralService(DeferralRepository deferralRepository) {
        this.deferralRepository = deferralRepository;
    }

    public EligibilityResult checkEligibility(DonorProfile donor, LocalDate today) {
        List<String> reasons = new ArrayList<>();

        if (donor.getDateOfBirth() != null) {
            int age = Period.between(donor.getDateOfBirth(), today).getYears();
            if (age < 18 || age > 60) {
                reasons.add("AGE_OUT_OF_RANGE");
            }
        }

        if (donor.getWeightKg() != null && donor.getWeightKg().compareTo(new BigDecimal("50.00")) < 0) {
            reasons.add("UNDERWEIGHT");
        }

        List<DonorDeferral> activeDeferrals = deferralRepository.findActiveDeferrals(donor.getId(), today);
        if (!activeDeferrals.isEmpty()) {
            reasons.add("ACTIVE_DEFERRAL");
        }

        return reasons.isEmpty() ? EligibilityResult.eligible() : EligibilityResult.ineligible(reasons);
    }
}
