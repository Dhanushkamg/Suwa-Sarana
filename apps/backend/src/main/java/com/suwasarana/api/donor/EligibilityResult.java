package com.suwasarana.api.donor;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class EligibilityResult {
    private final boolean eligible;
    private final List<String> reasons;
    private final LocalDate nextEligibleDate;

    private EligibilityResult(boolean eligible, List<String> reasons, LocalDate nextEligibleDate) {
        this.eligible = eligible;
        this.reasons = reasons;
        this.nextEligibleDate = nextEligibleDate;
    }

    public static EligibilityResult eligible() {
        return new EligibilityResult(true, Collections.emptyList(), null);
    }

    public static EligibilityResult ineligible(List<String> reasons) {
        return new EligibilityResult(false, reasons, null);
    }

    public static EligibilityResult ineligible(List<String> reasons, LocalDate nextEligibleDate) {
        return new EligibilityResult(false, reasons, nextEligibleDate);
    }

    public boolean isEligible() { return eligible; }
    public List<String> getReasons() { return reasons; }

    /**
     * First date on which the donor can donate again, when the only obstacles are time-based (a recent donation or
     * another active deferral). Null when eligible, or when an age/weight reason applies that time alone does not fix.
     */
    public LocalDate getNextEligibleDate() { return nextEligibleDate; }
}
