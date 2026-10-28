package com.suwasarana.api.donor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether a donor may donate on a given date. Every number comes from {@link DeferralRulesConfig}.
 *
 * <p>Reason codes (stable, localised by the frontend):
 * <ul>
 *   <li>{@code AGE_OUT_OF_RANGE} - younger than 18 or older than 60 on that date</li>
 *   <li>{@code AGE_ABOVE_FIRST_TIME_LIMIT} - 56 to 60 and has never donated through the platform (first-time limit is 55)</li>
 *   <li>{@code UNDERWEIGHT} - weight is not above 50 kg</li>
 *   <li>{@code RECENT_DONATION} - inside the 4-month interval after the last recorded donation</li>
 *   <li>{@code ACTIVE_DEFERRAL} - any other deferral row that is still active on that date</li>
 * </ul>
 * Pregnancy, illness, travel, haemoglobin and risk behaviour are NOT tracked here; the Medical Officer at the blood
 * bank decides those on the day.
 */
@Service
public class DeferralService {

    public static final String RECENT_DONATION = "RECENT_DONATION";

    private final DeferralRepository deferralRepository;

    @Autowired
    public DeferralService(DeferralRepository deferralRepository) {
        this.deferralRepository = deferralRepository;
    }

    /** Eligibility on {@code onDate} (today for matching, the slot date for bookings). */
    public EligibilityResult checkEligibility(DonorProfile donor, LocalDate onDate) {
        List<String> reasons = new ArrayList<>();

        if (donor.getDateOfBirth() != null) {
            int age = DeferralRulesConfig.ageOn(donor.getDateOfBirth(), onDate);
            boolean firstTime = donor.getTotalDonations() == null || donor.getTotalDonations() == 0;
            if (age < DeferralRulesConfig.MIN_DONOR_AGE || age > DeferralRulesConfig.MAX_DONOR_AGE) {
                reasons.add("AGE_OUT_OF_RANGE");
            } else if (!DeferralRulesConfig.isAgeAcceptable(age, firstTime)) {
                reasons.add("AGE_ABOVE_FIRST_TIME_LIMIT");
            }
        }

        if (!DeferralRulesConfig.isWeightAcceptable(donor.getWeightKg())) {
            reasons.add("UNDERWEIGHT");
        }

        LocalDate nextEligible = null;
        List<DonorDeferral> activeDeferrals = donor.getId() == null
                ? List.of()
                : deferralRepository.findActiveDeferrals(donor.getId(), onDate);
        if (!activeDeferrals.isEmpty()) {
            boolean recentDonation = activeDeferrals.stream().anyMatch(d -> RECENT_DONATION.equals(d.getReason()));
            boolean other = activeDeferrals.stream().anyMatch(d -> !RECENT_DONATION.equals(d.getReason()));
            if (recentDonation) reasons.add(RECENT_DONATION);
            if (other) reasons.add("ACTIVE_DEFERRAL");
            nextEligible = activeDeferrals.stream()
                    .map(DonorDeferral::getDeferredUntil)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            if (nextEligible != null) nextEligible = nextEligible.plusDays(1);
        }

        if (reasons.isEmpty()) return EligibilityResult.eligible();
        // A date only helps when time is the sole obstacle
        boolean onlyTime = reasons.stream().allMatch(r -> r.equals(RECENT_DONATION) || r.equals("ACTIVE_DEFERRAL"));
        return EligibilityResult.ineligible(reasons, onlyTime ? nextEligible : null);
    }
}
