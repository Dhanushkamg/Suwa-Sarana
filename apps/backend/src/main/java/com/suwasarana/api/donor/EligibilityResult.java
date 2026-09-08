package com.suwasarana.api.donor;

import java.util.Collections;
import java.util.List;

public class EligibilityResult {
    private final boolean eligible;
    private final List<String> reasons;

    private EligibilityResult(boolean eligible, List<String> reasons) {
        this.eligible = eligible;
        this.reasons = reasons;
    }

    public static EligibilityResult eligible() {
        return new EligibilityResult(true, Collections.emptyList());
    }

    public static EligibilityResult ineligible(List<String> reasons) {
        return new EligibilityResult(false, reasons);
    }

    public boolean isEligible() { return eligible; }
    public List<String> getReasons() { return reasons; }
}
