package com.suwasarana.api.admin.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TriageReviewDto {

    @Size(max = 200, message = "Notes must be at most 200 characters")
    private String notes;

    /** What the reviewer decided about the AI flag; optional, REVIEWED when absent. */
    @Pattern(regexp = "^(?i)(REVIEWED|DISMISSED|CONFIRMED)$", message = "Decision must be REVIEWED, DISMISSED or CONFIRMED")
    private String decision;

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
}
