package com.suwasarana.api.ai.kb;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One curated knowledge-base entry. Immutable. Answers already have their {{placeholders}} resolved.
 *
 * @param keywords         language ("en", "si", "ta", "singlish") to keyword phrases used for retrieval
 * @param questionExamples language to example questions (retrieval vocabulary, follow-up suggestions, evaluation)
 * @param answers          "en"/"si"/"ta" to answer text
 * @param needsHumanReview fact-level flag (the English content needs confirmation)
 * @param translationDraft "si"/"ta" to true when that translation is an unreviewed draft
 * @param companions       entries that are always appended when this one is used (for example a conflict note)
 * @param related          entries suggested as follow-up questions
 */
public record KbEntry(
        String id,
        String topic,
        KbStatus status,
        String category,
        Map<String, List<String>> keywords,
        Map<String, List<String>> questionExamples,
        Map<String, String> answers,
        KbSource source,
        KbSource secondarySource,
        LocalDate lastReviewed,
        String confidence,
        boolean needsHumanReview,
        String reviewReason,
        Map<String, Boolean> translationDraft,
        List<String> companions,
        List<String> related) {

    /** Answer in the given language ("en", "si", "ta"); falls back to English. */
    public String answer(String lang) {
        String a = answers.get(lang);
        return a != null && !a.isBlank() ? a : answers.get("en");
    }

    /** True when a reply built from this entry must tell the user to confirm with NBTS / the Medical Officer. */
    public boolean requiresConfirmation() {
        return status != KbStatus.VERIFIED || needsHumanReview;
    }
}
