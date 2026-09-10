package com.suwasarana.api.circle.dto;

import java.time.LocalDateTime;

public class CircleResponseDto {
    private Long id;
    private String responderName;
    private String responderPhone;
    private String bloodType;
    private String notes;
    private LocalDateTime respondedAt;

    public CircleResponseDto() {}

    public CircleResponseDto(Long id, String responderName, String responderPhone, String bloodType, String notes, LocalDateTime respondedAt) {
        this.id = id;
        this.responderName = responderName;
        this.responderPhone = responderPhone;
        this.bloodType = bloodType;
        this.notes = notes;
        this.respondedAt = respondedAt;
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

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
}
