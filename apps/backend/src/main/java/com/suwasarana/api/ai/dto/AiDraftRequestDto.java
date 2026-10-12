package com.suwasarana.api.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AiDraftRequestDto {

    @NotBlank(message = "Natural language text prompt is required")
    @Size(max = 2000, message = "Text must be at most 2000 characters")
    private String text;

    private String locale; // "en", "si", "ta"

    public AiDraftRequestDto() {
    }

    public AiDraftRequestDto(String text, String locale) {
        this.text = text;
        this.locale = locale;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
