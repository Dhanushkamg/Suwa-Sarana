package com.suwasarana.api.ivr;

import com.suwasarana.api.ai.kb.EligibilityFacts;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The only place the hotline gets the eligibility facts it reads aloud. It owns no number: every threshold is read
 * from {@link EligibilityFacts} (the versioned knowledge base), the same source as the chatbot and
 * {@code DeferralService}, so the phone line, the web assistant and the booking rules cannot disagree.
 *
 * <p>The published NBTS values (https://nbts.health.gov.lk/donate-blood/, reviewed 2026-10-27): age 18-60 (first-time
 * donors up to 55), weight above 50 kg, haemoglobin above 12.5 g/dL, at least 4 months between donations. For
 * everything the NBTS has not published (tattoos, surgery, medication, pregnancy ...) the hotline names no number and
 * refers callers to the medical officer. The former {@code app.ivr.facts.*} overrides, including the unsourced tattoo
 * and surgery months, were removed on purpose: a figure that is not in the knowledge base must not be spoken.
 */
@Component
public class IvrFacts {

    private final EligibilityFacts facts;

    public IvrFacts(EligibilityFacts facts) {
        this.facts = facts;
    }

    /** The sentence(s) read to a caller who pressed 2. */
    public String eligibilityScript() {
        List<String> parts = new ArrayList<>();
        parts.add("According to the National Blood Transfusion Service: donors must be between " + facts.getMinAge() + " and "
                + facts.getMaxAge() + " years old, and first-time donors must not be older than " + facts.getFirstTimeMaxAge() + ".");
        parts.add("You must weigh more than " + plain(facts.getWeightKgAbove()) + " kilograms and your haemoglobin must be above "
                + plain(facts.getHaemoglobinGdlAbove()) + " grams per decilitre.");
        parts.add("You must wait at least " + facts.getDonationIntervalMonths() + " months between donations.");
        parts.add("For tattoos, surgery, medicines, illness, pregnancy or recent travel, please ask the medical officer "
                + "at your nearest blood bank. The medical officer makes the final decision on the day. Thank you for saving lives.");
        return String.join(" ", parts);
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
