package com.suwasarana.api.ai.kb;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for the numeric NBTS eligibility thresholds, read from the {@code facts} block of the
 * knowledge base. The chatbot answers, and (once adopted) the matching engine ({@code DeferralRulesConfig},
 * {@code DeferralService}) and the IVR ({@code IvrFacts}), must read these values instead of hard-coding them.
 * <p>
 * Only VERIFIED figures are exposed. Tattoo, surgery, medicine, alcohol-hours and similar figures are deliberately
 * absent: NBTS publishes none, and the old invented numbers (12 months, 6 months, 7 days, 24 h ...) must not return.
 * <p>
 * Boundary semantics follow the official wording: weight and haemoglobin must be ABOVE the figure, so exactly
 * 50 kg or exactly 12.5 g/dL is NOT eligible. Age is inclusive (18 to 60, first-time donors up to 55).
 * The first-time donor weight (55 kg, press report) and the malaria look-back (2 years, press report) are unresolved
 * conflicts and are exposed only as {@code *PressReport} values for display, never for eligibility decisions.
 */
@Component
public class EligibilityFacts {

    private final int minAge;
    private final int maxAge;
    private final int firstTimeMaxAge;
    private final BigDecimal weightKgAbove;
    private final BigDecimal haemoglobinGdlAbove;
    private final int donationIntervalMonths;
    private final int malariaDeferralYears;
    private final int otherTravelDeferralMonths;
    private final int mealWithinHours;
    private final int sleepHours;
    private final int firstTimeWeightKgPressReport;
    private final int malariaDeferralYearsPressReport;

    public EligibilityFacts(KnowledgeBase kb) {
        Map<String, String> f = kb.facts();
        this.minAge = Integer.parseInt(req(f, "minAge"));
        this.maxAge = Integer.parseInt(req(f, "maxAge"));
        this.firstTimeMaxAge = Integer.parseInt(req(f, "firstTimeMaxAge"));
        this.weightKgAbove = new BigDecimal(req(f, "minWeightKgAbove"));
        this.haemoglobinGdlAbove = new BigDecimal(req(f, "haemoglobinAboveGdl"));
        this.donationIntervalMonths = Integer.parseInt(req(f, "donationIntervalMonths"));
        this.malariaDeferralYears = Integer.parseInt(req(f, "malariaDeferralYears"));
        this.otherTravelDeferralMonths = Integer.parseInt(req(f, "otherTravelDeferralMonths"));
        this.mealWithinHours = Integer.parseInt(req(f, "mealWithinHours"));
        this.sleepHours = Integer.parseInt(req(f, "sleepHours"));
        this.firstTimeWeightKgPressReport = Integer.parseInt(req(f, "firstTimeWeightKgPressReport"));
        this.malariaDeferralYearsPressReport = Integer.parseInt(req(f, "malariaDeferralYearsPressReport"));
    }

    private static String req(Map<String, String> f, String key) {
        String v = f.get(key);
        if (v == null) throw new IllegalStateException("Knowledge base facts are missing '" + key + "'");
        return v;
    }

    public int getMinAge() { return minAge; }
    public int getMaxAge() { return maxAge; }
    public int getFirstTimeMaxAge() { return firstTimeMaxAge; }
    /** Weight must be strictly above this many kilograms. */
    public BigDecimal getWeightKgAbove() { return weightKgAbove; }
    /** Haemoglobin must be strictly above this many g/dL. */
    public BigDecimal getHaemoglobinGdlAbove() { return haemoglobinGdlAbove; }
    public int getDonationIntervalMonths() { return donationIntervalMonths; }
    public int getMalariaDeferralYears() { return malariaDeferralYears; }
    public int getOtherTravelDeferralMonths() { return otherTravelDeferralMonths; }
    public int getMealWithinHours() { return mealWithinHours; }
    public int getSleepHours() { return sleepHours; }
    /** UNRESOLVED conflict: press-reported first-time weight. Display only. */
    public int getFirstTimeWeightKgPressReport() { return firstTimeWeightKgPressReport; }
    /** UNRESOLVED conflict: press-reported malaria look-back. Display only. */
    public int getMalariaDeferralYearsPressReport() { return malariaDeferralYearsPressReport; }

    public boolean isAgeEligible(int ageYears, boolean firstTimeDonor) {
        return ageYears >= minAge && ageYears <= (firstTimeDonor ? firstTimeMaxAge : maxAge);
    }

    public boolean isWeightEligible(BigDecimal kg) {
        return kg != null && kg.compareTo(weightKgAbove) > 0;
    }

    public boolean isHaemoglobinEligible(BigDecimal gdl) {
        return gdl != null && gdl.compareTo(haemoglobinGdlAbove) > 0;
    }

    /** First date a donor may give whole blood again (calendar months, not 120 days). */
    public LocalDate nextEligibleDonationDate(LocalDate lastDonation) {
        return lastDonation.plusMonths(donationIntervalMonths);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("minAge", minAge);
        m.put("maxAge", maxAge);
        m.put("firstTimeMaxAge", firstTimeMaxAge);
        m.put("weightKgAbove", weightKgAbove);
        m.put("haemoglobinGdlAbove", haemoglobinGdlAbove);
        m.put("donationIntervalMonths", donationIntervalMonths);
        m.put("malariaDeferralYears", malariaDeferralYears);
        m.put("otherTravelDeferralMonths", otherTravelDeferralMonths);
        return m;
    }
}
