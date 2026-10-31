package com.suwasarana.api.ai;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Transparent outcome of the rule-based request triage: the (capped) score, the ordered reasons that produced it
 * and whether a human should look at the request. The AI never changes a request's status; a person decides.
 *
 * @param score                  0-100 (the sum of the reason weights, capped)
 * @param rawScore               the uncapped sum
 * @param reasons                reasons ordered by weight, heaviest first; the frontend localises by {@code code}
 * @param humanReviewRecommended true when the score reaches {@link #REVIEW_THRESHOLD}
 */
public record TriageResult(int score, int rawScore, List<Reason> reasons, String engineVersion, LocalDateTime evaluatedAt,
                           boolean humanReviewRecommended) {

    public static final int REVIEW_THRESHOLD = 40;
    public static final String ENGINE_VERSION = "rules-v2";

    /**
     * One contribution to the score.
     *
     * @param code   stable machine code (REQUESTER_HIGH_FREQUENCY, REQUESTER_REPEAT_24H, UNVERIFIED_HIGH_UNITS,
     *               UNVERIFIED_MODERATE_UNITS, SUSPICIOUS_HOSPITAL_NAME, UNVERIFIED_CRITICAL)
     * @param weight points added to the score
     * @param detail English explanation for logs and admin screens that are not localised yet
     * @param params values to fill a localised sentence (counts, unit numbers ...)
     */
    public record Reason(String code, int weight, String detail, Map<String, Object> params) {
    }
}
