package com.suwasarana.api.ivr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The only place the hotline gets the eligibility facts it reads aloud ({@code app.ivr.facts.*}).
 *
 * <p>Defaults are the values published by the National Blood Transfusion Service
 * (https://nbts.health.gov.lk/donate-blood/, reviewed 2026-10-27 in docs/audit/ai.md and
 * docs/audit/kb-candidates.json): age 18-60 (first-time donors up to 55), weight above 50 kg, haemoglobin above
 * 12.5 g/dL, at least 4 months between donations. For everything the NBTS has not published (tattoos, surgery,
 * medication ...) the hotline names no number and refers callers to the medical officer. {@code tattooMonths} and
 * {@code surgeryMonths} are therefore unset by default and only spoken when an operator sets them after getting
 * written confirmation from the NBTS.
 *
 * <p>Known contradictions this class replaces (see the report of this change): the old script said "wait 3 to
 * 4 months" between donations and "12 months" after surgery or a tattoo, while {@code DeferralRulesConfig} says 120 days,
 * 6 months (surgery) and 12 months (tattoo/piercing), and the official page says 4 months and is silent on the rest.
 */
@Component
@ConfigurationProperties(prefix = "app.ivr.facts")
public class IvrFacts {

    private int minAge = 18;
    private int maxAge = 60;
    private int firstTimeMaxAge = 55;
    private int weightAboveKg = 50;
    private double haemoglobinAbove = 12.5;
    private int intervalMonths = 4;
    /** Unset = not officially published, not spoken. */
    private Integer tattooMonths;
    /** Unset = not officially published, not spoken. */
    private Integer surgeryMonths;

    /** The sentence(s) read to a caller who pressed 2. */
    public String eligibilityScript() {
        List<String> parts = new ArrayList<>();
        parts.add("According to the National Blood Transfusion Service: donors must be between " + minAge + " and " + maxAge
                + " years old, and first-time donors must not be older than " + firstTimeMaxAge + ".");
        parts.add("You must weigh more than " + weightAboveKg + " kilograms and your haemoglobin must be above "
                + trimmed(haemoglobinAbove) + " grams per decilitre.");
        parts.add("You must wait at least " + intervalMonths + " months between donations.");
        if (tattooMonths != null) {
            parts.add("After a tattoo or piercing, wait " + tattooMonths + " months.");
        }
        if (surgeryMonths != null) {
            parts.add("After surgery, wait " + surgeryMonths + " months.");
        }
        parts.add("For tattoos, surgery, medicines, illness, pregnancy or recent travel, please ask the medical officer "
                + "at your nearest blood bank. The medical officer makes the final decision on the day. Thank you for saving lives.");
        return String.join(" ", parts);
    }

    private static String trimmed(double v) {
        return v == Math.floor(v) ? Integer.toString((int) v) : Double.toString(v);
    }

    public int getMinAge() { return minAge; }
    public void setMinAge(int minAge) { this.minAge = minAge; }
    public int getMaxAge() { return maxAge; }
    public void setMaxAge(int maxAge) { this.maxAge = maxAge; }
    public int getFirstTimeMaxAge() { return firstTimeMaxAge; }
    public void setFirstTimeMaxAge(int firstTimeMaxAge) { this.firstTimeMaxAge = firstTimeMaxAge; }
    public int getWeightAboveKg() { return weightAboveKg; }
    public void setWeightAboveKg(int weightAboveKg) { this.weightAboveKg = weightAboveKg; }
    public double getHaemoglobinAbove() { return haemoglobinAbove; }
    public void setHaemoglobinAbove(double haemoglobinAbove) { this.haemoglobinAbove = haemoglobinAbove; }
    public int getIntervalMonths() { return intervalMonths; }
    public void setIntervalMonths(int intervalMonths) { this.intervalMonths = intervalMonths; }
    public Integer getTattooMonths() { return tattooMonths; }
    public void setTattooMonths(Integer tattooMonths) { this.tattooMonths = tattooMonths; }
    public Integer getSurgeryMonths() { return surgeryMonths; }
    public void setSurgeryMonths(Integer surgeryMonths) { this.surgeryMonths = surgeryMonths; }
}
