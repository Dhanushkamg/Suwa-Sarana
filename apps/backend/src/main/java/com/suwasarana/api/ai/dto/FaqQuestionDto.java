package com.suwasarana.api.ai.dto;

import jakarta.validation.constraints.NotBlank;

public class FaqQuestionDto {

    @NotBlank(message = "Question cannot be blank")
    private String question;

    private String locale; // "en", "si", "ta"

    public FaqQuestionDto() {
    }

    public FaqQuestionDto(String question, String locale) {
        this.question = question;
        this.locale = locale;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
