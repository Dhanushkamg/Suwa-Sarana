package com.suwasarana.api.ai.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response data of {@code POST /api/faq/ask}.
 * <p>
 * {@code grounded} is true only when at least one knowledge-base entry was used. {@code needsConfirmation} is true when
 * any used entry is a CONFLICT/GAP or is flagged for human review: the UI must show the "confirm with NBTS" state, not
 * a plain "verified" badge. {@code groundedInRules} is DEPRECATED and equals {@code grounded}.
 */
public class FaqAnswerDto {

    /** One cited knowledge-base source. */
    public static class SourceDto {
        private String id;
        private String title;
        private String url;
        private String lastReviewed;
        private String status;

        public SourceDto() {
        }

        public SourceDto(String id, String title, String url, String lastReviewed, String status) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.lastReviewed = lastReviewed;
            this.status = status;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getLastReviewed() { return lastReviewed; }
        public void setLastReviewed(String lastReviewed) { this.lastReviewed = lastReviewed; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    private String answer;
    /** Reply language: en | si | ta. */
    private String language;
    /** Deprecated alias of {@link #language}. */
    private String locale;
    /** Detected input style: en | si | ta | singlish | tanglish | unknown. */
    private String inputStyle;
    /** script | romanised. */
    private String replyStyle = "script";
    /** ANSWER | NO_ANSWER | EMERGENCY | OFF_TOPIC | REFUSAL | GREETING | SMALL_TALK | PLATFORM | EMPTY. */
    private String kind;
    private boolean grounded;
    private boolean groundedInRules;
    private List<SourceDto> sources = new ArrayList<>();
    private boolean needsConfirmation;
    private String disclaimer;
    private boolean usedLlm;
    private List<String> followUps = new ArrayList<>();
    /** True when the reply language is si/ta and a used translation has not been reviewed by a native speaker. */
    private boolean translationDraft;
    private String translationNote;

    public FaqAnswerDto() {
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; this.locale = language; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getInputStyle() { return inputStyle; }
    public void setInputStyle(String inputStyle) { this.inputStyle = inputStyle; }
    public String getReplyStyle() { return replyStyle; }
    public void setReplyStyle(String replyStyle) { this.replyStyle = replyStyle; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public boolean isGrounded() { return grounded; }
    public void setGrounded(boolean grounded) { this.grounded = grounded; this.groundedInRules = grounded; }
    /** @deprecated equals {@link #isGrounded()}; kept for the old frontend. */
    @Deprecated
    public boolean isGroundedInRules() { return groundedInRules; }
    public void setGroundedInRules(boolean groundedInRules) { this.groundedInRules = groundedInRules; }
    public List<SourceDto> getSources() { return sources; }
    public void setSources(List<SourceDto> sources) { this.sources = sources; }
    public boolean isNeedsConfirmation() { return needsConfirmation; }
    public void setNeedsConfirmation(boolean needsConfirmation) { this.needsConfirmation = needsConfirmation; }
    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }
    public boolean isUsedLlm() { return usedLlm; }
    public void setUsedLlm(boolean usedLlm) { this.usedLlm = usedLlm; }
    public List<String> getFollowUps() { return followUps; }
    public void setFollowUps(List<String> followUps) { this.followUps = followUps; }
    public boolean isTranslationDraft() { return translationDraft; }
    public void setTranslationDraft(boolean translationDraft) { this.translationDraft = translationDraft; }
    public String getTranslationNote() { return translationNote; }
    public void setTranslationNote(String translationNote) { this.translationNote = translationNote; }
}
