package com.suwasarana.api.donor;

import com.suwasarana.api.ai.kb.EligibilityFacts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The platform's view of the donor-eligibility numbers. It owns NO number itself: every threshold is read from
 * {@link EligibilityFacts}, which is built from the {@code facts} block of the versioned knowledge base
 * ({@code kb/nbts-eligibility.v1.json}). {@link DeferralService} (matching, slot booking and the eligibility endpoint),
 * the analytics "available donors" count, the donor-profile weight validation, the IVR script and the FAQ chatbot all
 * end up at the same values.
 *
 * <h3>Enforced (VERIFIED on https://nbts.health.gov.lk/donate-blood/, accessed 2026-10-27)</h3>
 * age 18 to 60 inclusive (first-time donors up to 55 where the donation history is known), weight strictly above
 * 50 kg, 4 calendar months between whole-blood donations. Haemoglobin (above 12.5 g/dL), malaria and travel look-backs
 * are measured or asked at the blood bank and are not tracked per donor.
 *
 * <h3>Deliberately NOT enforced</h3>
 * Tattoo or piercing, surgery, dental work, antibiotics, alcohol and pregnancy periods. The NBTS page publishes no
 * figure for them and the numbers that used to live here (12 months, 6 months, 7 days, 24 hours ...) had no source. They
 * were removed instead of being kept as constants (audit AI-04 / F-07); the Medical Officer decides these on the day.
 *
 * <h3>Open decisions</h3>
 * A press article quotes more than 55 kg for first-time donors (kb-candidates {@code CONFLICT-FIRST-TIME-WEIGHT});
 * the NBTS page value is enforced until NBTS confirms in writing. Set {@code app.eligibility.owner-confirmed=true}
 * once the owner has confirmed the verified values; the eligibility response reports the flag so the UI can show or hide
 * its "confirm with the blood bank" notice.
 */
@Component
public class DeferralRulesConfig {

    /** Where the verified numbers come from, echoed by the eligibility endpoint. */
    public static final String SOURCE = "NBTS Sri Lanka donor page, https://nbts.health.gov.lk/donate-blood/ (accessed 2026-10-27)";

    private final EligibilityFacts facts;

    @Value("${app.eligibility.owner-confirmed:false}")
    private boolean ownerConfirmed;

    @Autowired
    public DeferralRulesConfig(EligibilityFacts facts) {
        this.facts = facts;
    }

    public boolean isOwnerConfirmed() {
        return ownerConfirmed;
    }

    // ---------------------------------------------------------------- thresholds (all from EligibilityFacts)

    /** Donors must be at least this old. */
    public int minAge() { return facts.getMinAge(); }

    /** Donors may be at most this old (inclusive). */
    public int maxAge() { return facts.getMaxAge(); }

    /** First-time donors may be at most this old (inclusive). */
    public int firstTimeMaxAge() { return facts.getFirstTimeMaxAge(); }

    /** Donors must weigh MORE than this (the NBTS page says "above 50 kg"). */
    public BigDecimal minWeightKgExclusive() { return facts.getWeightKgAbove(); }

    /** Minimum gap between whole-blood donations, in calendar months (one value for all donors). */
    public int donationIntervalMonths() { return facts.getDonationIntervalMonths(); }

    /** Haemoglobin must be above this (g/dL). Measured at the blood bank; the platform does not track it. */
    public BigDecimal minHaemoglobinGdlExclusive() { return facts.getHaemoglobinGdlAbove(); }

    // ---------------------------------------------------------------- evaluation helpers

    public static int ageOn(LocalDate dateOfBirth, LocalDate on) {
        return Period.between(dateOfBirth, on).getYears();
    }

    /** True when {@code age} is acceptable for a donor with the given history. */
    public boolean isAgeAcceptable(int age, boolean firstTimeDonor) {
        return facts.isAgeEligible(age, firstTimeDonor);
    }

    /** True when the weight is strictly above the minimum. A missing weight is "unknown", not a failure. */
    public boolean isWeightAcceptable(BigDecimal weightKg) {
        return weightKg == null || facts.isWeightEligible(weightKg);
    }

    /** First day a donor who gave blood on {@code donatedOn} may donate again (calendar months, not 120 days). */
    public LocalDate nextEligibleDateAfterDonation(LocalDate donatedOn) {
        return facts.nextEligibleDonationDate(donatedOn);
    }

    /** Last day (inclusive) of the deferral that follows a donation, as stored in donor_deferrals.deferred_until. */
    public LocalDate deferralEndAfterDonation(LocalDate donatedOn) {
        return nextEligibleDateAfterDonation(donatedOn).minusDays(1);
    }

    /** The verified numbers only, for the eligibility endpoint and for UIs that must not hard-code them. */
    public Map<String, Object> verifiedRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("minAge", minAge());
        rules.put("maxAge", maxAge());
        rules.put("maxAgeFirstTimeDonor", firstTimeMaxAge());
        rules.put("minWeightKgExclusive", minWeightKgExclusive());
        rules.put("donationIntervalMonths", donationIntervalMonths());
        rules.put("minHaemoglobinGdLExclusive", minHaemoglobinGdlExclusive());
        rules.put("source", SOURCE);
        rules.put("ownerConfirmed", ownerConfirmed);
        return rules;
    }
}
