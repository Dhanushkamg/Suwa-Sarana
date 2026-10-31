package com.suwasarana.api.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One stored AI triage evaluation and, once a person has looked at it, who reviewed it, when and what they decided. */
@Entity
@Table(name = "ai_triage_records")
public class AiTriageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "raw_score", nullable = false)
    private int rawScore;

    @Column(name = "reasons_json", nullable = false, columnDefinition = "TEXT")
    private String reasonsJson;

    @Column(name = "engine_version", nullable = false, length = 30)
    private String engineVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_decision", length = 20)
    private String reviewDecision;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getRawScore() { return rawScore; }
    public void setRawScore(int rawScore) { this.rawScore = rawScore; }
    public String getReasonsJson() { return reasonsJson; }
    public void setReasonsJson(String reasonsJson) { this.reasonsJson = reasonsJson; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewDecision() { return reviewDecision; }
    public void setReviewDecision(String reviewDecision) { this.reviewDecision = reviewDecision; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
}
