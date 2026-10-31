package com.suwasarana.api.circle.dto;

import com.suwasarana.api.common.Times;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CircleResponseDto {
    private Long id;
    private String responderName;
    private String responderPhone;
    private String bloodType;
    private String notes;
    private Instant respondedAt;
    /**
     * Only set on the answer to a new pledge: {@code true} when the requester was really notified, {@code false}
     * when the pledge was saved but no notification channel delivered it. Absent in the requester's list.
     */
    private Boolean requesterNotified;

    public CircleResponseDto() {}

    public CircleResponseDto(Long id, String responderName, String responderPhone, String bloodType, String notes, LocalDateTime respondedAt) {
        this.id = id;
        this.responderName = responderName;
        this.responderPhone = responderPhone;
        this.bloodType = bloodType;
        this.notes = notes;
        this.respondedAt = Times.instantOf(respondedAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getResponderName() { return responderName; }
    public void setResponderName(String responderName) { this.responderName = responderName; }

    public String getResponderPhone() { return responderPhone; }
    public void setResponderPhone(String responderPhone) { this.responderPhone = responderPhone; }

    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = Times.instantOf(respondedAt); }

    public Boolean getRequesterNotified() { return requesterNotified; }
    public void setRequesterNotified(Boolean requesterNotified) { this.requesterNotified = requesterNotified; }
}
