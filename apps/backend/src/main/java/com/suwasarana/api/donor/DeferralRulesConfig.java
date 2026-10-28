package com.suwasarana.api.donor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single source of truth for donor-eligibility numbers. {@link DeferralService} (matching, slot booking and the
 * eligibility endpoint), analytics ("available donors") and the FAQ all read from here; nothing else may hard-code
 * an age, weight or interval.
 *
 * <h3>VERIFIED values</h3>
 * Taken from the National Blood Transfusion Service donor page, https://nbts.health.gov.lk/donate-blood/ (accessed
 * 2026-10-27), see {@code docs/audit/kb-candidates.json} entries ELIG-AGE, ELIG-AGE-FIRST-TIME, ELIG-WEIGHT,
 * ELIG-HAEMOGLOBIN, ELIG-INTERVAL, TRAVEL-MALARIA and TRAVEL-OTHER. These are the only numbers the platform enforces.
 *
 * <h3>OWNER CONFIRMATION REQUIRED</h3>
 * <ul>
 *   <li>Conflict A (kb-candidates CONFLICT-FIRST-TIME-WEIGHT): a press article quotes first-time donors as needing
 *       more than 55 kg; the NBTS page says "above 50 kg" for everyone. The page value is enforced. Do not change it
 *       until NBTS confirms in writing.</li>
 *   <li>Everything under "UNVERIFIED" below has no source on the NBTS page (kb-candidates GAP-* entries). They are
 *       kept ONLY because other modules still read the constants; {@link DeferralService} does not enforce them and
 *       the eligibility endpoint never reports them. Do not present them to donors as rules.</li>
 *   <li>Set {@code app.eligibility.owner-confirmed=true} once the owner has confirmed the verified values with NBTS;
 *       the eligibility response reports the flag so the UI can show or hide its "confirm with the blood bank" notice.</li>
 * </ul>
 */
@Component
public class DeferralRulesConfig {

    // ---------------------------------------------------------------- VERIFIED (enforced)

    /** Donors must be at least this old. */
    public static final int MIN_DONOR_AGE = 18;
    /** Donors may be at most this old (inclusive). */
    public static final int MAX_DONOR_AGE = 60;
    /** First-time donors may be at most this old (inclusive). */
    public static final int MAX_FIRST_TIME_DONOR_AGE = 55;
    /** Donors must weigh MORE than this (the NBTS page says "above 50 kg"). */
    public static final BigDecimal MIN_DONOR_WEIGHT_KG = new BigDecimal("50.00");
    /** Minimum gap between whole-blood donations, in calendar months (one value for all donors). */
    public static final int DONATION_INTERVAL_MONTHS = 4;
    /** Haemoglobin must be above this (g/dL). Measured at the blood bank; the platform does not track it. */
    public static final BigDecimal MIN_HAEMOGLOBIN_G_DL = new BigDecimal("12.5");
    /** Deferral after travel to a malaria-endemic country on the NBTS 2024 list. Not tracked per donor. */
    public static final int MALARIA_TRAVEL_DEFERRAL_YEARS = 3;
    /** Deferral after other foreign travel. Not tracked per donor. */
    public static final int OTHER_TRAVEL_DEFERRAL_MONTHS = 3;

    /**
     * Approximation of {@link #DONATION_INTERVAL_MONTHS} in days, kept for modules that still format a day count.
     * Never use it to compute a date: use {@link #nextEligibleDateAfterDonation(LocalDate)}.
     */
    @Deprecated
    public static final int DONATION_INTERVAL_DAYS = 120; // 4 months

    // ---------------------------------------------------------------- UNVERIFIED (not enforced, see class comment)

    /** UNVERIFIED: NBTS page is silent. A press quote says "tattoo over two years old". Owner/NBTS to confirm. */
    public static final int TATTOO_PIERCING_DEFERRAL_MONTHS = 12;
    /** UNVERIFIED: no NBTS source. */
    public static final int SURGERY_DEFERRAL_MONTHS = 6;
    /** UNVERIFIED: no NBTS source. */
    public static final int DENTAL_EXTRACTION_DEFERRAL_DAYS = 7;
    /** UNVERIFIED: no NBTS source. */
    public static final int ANTIBIOTICS_DEFERRAL_DAYS = 7;
    /** UNVERIFIED: NBTS says only "avoid alcohol" with no hours. */
    public static final int ALCOHOL_DEFERRAL_HOURS = 24;
    /** UNVERIFIED: NBTS says only "must not be pregnant". */
    public static final int PREGNANCY_DEFERRAL_MONTHS = 12;

    /** Where the verified numbers come from, echoed by the eligibility endpoint. */
    public static final String SOURCE = "NBTS Sri Lanka donor page, https://nbts.health.gov.lk/donate-blood/ (accessed 2026-10-27)";

    @Value("${app.eligibility.owner-confirmed:false}")
    private boolean ownerConfirmed;

    public boolean isOwnerConfirmed() {
        return ownerConfirmed;
    }

    // ---------------------------------------------------------------- evaluation helpers (pure functions)

    public static int ageOn(LocalDate dateOfBirth, LocalDate on) {
        return Period.between(dateOfBirth, on).getYears();
    }

    /** True when {@code age} is acceptable for a donor with the given history. */
    public static boolean isAgeAcceptable(int age, boolean firstTimeDonor) {
        if (age < MIN_DONOR_AGE || age > MAX_DONOR_AGE) return false;
        return !(firstTimeDonor && age > MAX_FIRST_TIME_DONOR_AGE);
    }

    /** True when the weight is strictly above the minimum. A missing weight is "unknown", not a failure. */
    public static boolean isWeightAcceptable(BigDecimal weightKg) {
        return weightKg == null || weightKg.compareTo(MIN_DONOR_WEIGHT_KG) > 0;
    }

    /** First day a donor who gave blood on {@code donatedOn} may donate again. */
    public static LocalDate nextEligibleDateAfterDonation(LocalDate donatedOn) {
        return donatedOn.plusMonths(DONATION_INTERVAL_MONTHS);
    }

    /** Last day (inclusive) of the deferral that follows a donation, as stored in donor_deferrals.deferred_until. */
    public static LocalDate deferralEndAfterDonation(LocalDate donatedOn) {
        return nextEligibleDateAfterDonation(donatedOn).minusDays(1);
    }

    /** The verified numbers only, for the eligibility endpoint and for UIs that must not hard-code them. */
    public Map<String, Object> verifiedRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("minAge", MIN_DONOR_AGE);
        rules.put("maxAge", MAX_DONOR_AGE);
        rules.put("maxAgeFirstTimeDonor", MAX_FIRST_TIME_DONOR_AGE);
        rules.put("minWeightKgExclusive", MIN_DONOR_WEIGHT_KG);
        rules.put("donationIntervalMonths", DONATION_INTERVAL_MONTHS);
        rules.put("minHaemoglobinGdLExclusive", MIN_HAEMOGLOBIN_G_DL);
        rules.put("source", SOURCE);
        rules.put("ownerConfirmed", ownerConfirmed);
        return rules;
    }

    public Map<String, Object> getAllRules() {
        Map<String, Object> rules = new LinkedHashMap<>(verifiedRules());
        rules.put("minWeightKg", MIN_DONOR_WEIGHT_KG);
        rules.put("donationIntervalDays", DONATION_INTERVAL_DAYS);
        // Unverified: present for older callers only
        rules.put("tattooPiercingDeferralMonths", TATTOO_PIERCING_DEFERRAL_MONTHS);
        rules.put("surgeryDeferralMonths", SURGERY_DEFERRAL_MONTHS);
        rules.put("dentalExtractionDeferralDays", DENTAL_EXTRACTION_DEFERRAL_DAYS);
        rules.put("antibioticsDeferralDays", ANTIBIOTICS_DEFERRAL_DAYS);
        rules.put("alcoholDeferralHours", ALCOHOL_DEFERRAL_HOURS);
        rules.put("pregnancyDeferralMonths", PREGNANCY_DEFERRAL_MONTHS);
        return rules;
    }

    public String generateSystemPromptContext() {
        return "OFFICIAL NATIONAL BLOOD TRANSFUSION SERVICE (NBTS) SRI LANKA DONATION RULES:\n" +
                "1. Age: Must be between " + MIN_DONOR_AGE + " and " + MAX_DONOR_AGE + " years old (first-time donors: up to " + MAX_FIRST_TIME_DONOR_AGE + ").\n" +
                "2. Weight: Must weigh more than " + MIN_DONOR_WEIGHT_KG + " kg.\n" +
                "3. Donation Frequency: Minimum " + DONATION_INTERVAL_DAYS + " days (" + DONATION_INTERVAL_MONTHS + " months) interval between whole blood donations.\n" +
                "4. Tattoos & Body Piercings: Temporary deferral of " + TATTOO_PIERCING_DEFERRAL_MONTHS + " months (1 year) from the date of the procedure. [not on the NBTS page: confirm with the blood bank]\n" +
                "5. Major Surgery: Temporary deferral of " + SURGERY_DEFERRAL_MONTHS + " to 12 months depending on clinical recovery. [not on the NBTS page: confirm with the blood bank]\n" +
                "6. Dental Extraction: Defer for " + DENTAL_EXTRACTION_DEFERRAL_DAYS + " days after the procedure/antibiotics. [not on the NBTS page: confirm with the blood bank]\n" +
                "7. Antibiotics / Acute Infection: Defer for " + ANTIBIOTICS_DEFERRAL_DAYS + " days after finishing full course of antibiotics. [not on the NBTS page: confirm with the blood bank]\n" +
                "8. Alcohol: Avoid alcohol before donating. [NBTS gives no number of hours]\n" +
                "9. Pregnancy: Donors must not be pregnant. [NBTS gives no post-delivery period]\n" +
                "10. General Health: Must feel well on the day of donation, have normal blood pressure and hemoglobin (above " + MIN_HAEMOGLOBIN_G_DL + " g/dL).\n" +
                "CLINICAL SAFETY MANDATE: You must NEVER diagnose illnesses, recommend medications, or give speculative medical clearance. If the user asks about a complex, rare, or unlisted medical condition, you MUST explicitly advise: 'Please consult the Medical Officer or clinical staff at your nearest blood bank for clearance.'";
    }
}
