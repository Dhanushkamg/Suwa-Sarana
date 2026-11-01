package com.suwasarana.api.admin.dto;

import com.suwasarana.api.common.Times;
import com.suwasarana.api.request.dto.RequestResponseDto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A request in the admin triage queue: the usual request fields plus the AI's explained result and the human review.
 * {@code fraudRiskScore} is the AI score and is never changed by a review.
 */
public class TriageQueueItemDto extends RequestResponseDto {

    /** The reasons behind the score, heaviest first. The frontend localises by code. */
    private List<Reason> reasons = List.of();
    private String engineVersion;
    private boolean humanReviewRecommended;
    private boolean reviewed;
    private Long reviewedBy;
    private Instant reviewedAt;
    private String reviewDecision;
    private String reviewNote;

    public record Reason(String code, int weight) {
    }

    public List<Reason> getReasons() { return reasons; }
    public void setReasons(List<Reason> reasons) { this.reasons = reasons; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public boolean isHumanReviewRecommended() { return humanReviewRecommended; }
    public void setHumanReviewRecommended(boolean humanReviewRecommended) { this.humanReviewRecommended = humanReviewRecommended; }
    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean reviewed) { this.reviewed = reviewed; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime utc) { this.reviewedAt = Times.instantOf(utc); }
    public String getReviewDecision() { return reviewDecision; }
    public void setReviewDecision(String reviewDecision) { this.reviewDecision = reviewDecision; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
}
