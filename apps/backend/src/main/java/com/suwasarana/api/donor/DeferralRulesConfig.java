package com.suwasarana.api.donor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DeferralRulesConfig {

    public static final int MIN_DONOR_AGE = 18;
    public static final int MAX_DONOR_AGE = 60;
    public static final BigDecimal MIN_DONOR_WEIGHT_KG = new BigDecimal("50.00");
    public static final int DONATION_INTERVAL_DAYS = 120; // 4 months
    public static final int TATTOO_PIERCING_DEFERRAL_MONTHS = 12; // 1 year
    public static final int SURGERY_DEFERRAL_MONTHS = 6;
    public static final int DENTAL_EXTRACTION_DEFERRAL_DAYS = 7;
    public static final int ANTIBIOTICS_DEFERRAL_DAYS = 7;
    public static final int ALCOHOL_DEFERRAL_HOURS = 24;
    public static final int PREGNANCY_DEFERRAL_MONTHS = 12;

    public Map<String, Object> getAllRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("minAge", MIN_DONOR_AGE);
        rules.put("maxAge", MAX_DONOR_AGE);
        rules.put("minWeightKg", MIN_DONOR_WEIGHT_KG);
        rules.put("donationIntervalDays", DONATION_INTERVAL_DAYS);
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
                "1. Age: Must be between " + MIN_DONOR_AGE + " and " + MAX_DONOR_AGE + " years old.\n" +
                "2. Weight: Must weigh at least " + MIN_DONOR_WEIGHT_KG + " kg.\n" +
                "3. Donation Frequency: Minimum " + DONATION_INTERVAL_DAYS + " days (4 months) interval between whole blood donations.\n" +
                "4. Tattoos & Body Piercings: Temporary deferral of " + TATTOO_PIERCING_DEFERRAL_MONTHS + " months (1 year) from the date of the procedure.\n" +
                "5. Major Surgery: Temporary deferral of " + SURGERY_DEFERRAL_MONTHS + " to 12 months depending on clinical recovery.\n" +
                "6. Dental Extraction: Defer for " + DENTAL_EXTRACTION_DEFERRAL_DAYS + " days after the procedure/antibiotics.\n" +
                "7. Antibiotics / Acute Infection: Defer for " + ANTIBIOTICS_DEFERRAL_DAYS + " days after finishing full course of antibiotics.\n" +
                "8. Alcohol: Avoid alcohol for at least " + ALCOHOL_DEFERRAL_HOURS + " hours before donating.\n" +
                "9. Pregnancy & Breastfeeding: Defer during pregnancy and for " + PREGNANCY_DEFERRAL_MONTHS + " months after delivery/breastfeeding.\n" +
                "10. General Health: Must feel well on the day of donation, have normal blood pressure and hemoglobin (>= 12.5 g/dL).\n" +
                "CLINICAL SAFETY MANDATE: You must NEVER diagnose illnesses, recommend medications, or give speculative medical clearance. If the user asks about a complex, rare, or unlisted medical condition, you MUST explicitly advise: 'Please consult the Medical Officer or clinical staff at your nearest blood bank for clearance.'";
    }
}
