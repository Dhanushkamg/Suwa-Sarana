package com.suwasarana.api.circle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SubmitCircleResponseDto {

    @NotBlank(message = "Responder name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String responderName;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String responderPhone;

    @NotBlank(message = "Blood type is required")
    @Pattern(regexp = "^(A\\+|A-|B\\+|B-|AB\\+|AB-|O\\+|O-)$", message = "Invalid blood type format (must be A+, A-, B+, B-, AB+, AB-, O+, O-)")
    private String bloodType;

    private String notes;

    public SubmitCircleResponseDto() {}

    public SubmitCircleResponseDto(String responderName, String responderPhone, String bloodType, String notes) {
        this.responderName = responderName;
        this.responderPhone = responderPhone;
        this.bloodType = bloodType;
        this.notes = notes;
    }

    public String getResponderName() { return responderName; }
    public void setResponderName(String responderName) { this.responderName = responderName; }

    public String getResponderPhone() { return responderPhone; }
    public void setResponderPhone(String responderPhone) { this.responderPhone = responderPhone; }

    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
