package com.suwasarana.api.admin.dto;

import jakarta.validation.constraints.Size;

public class TriageReviewDto {

    @Size(max = 200, message = "Notes must be at most 200 characters")
    private String notes;

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
