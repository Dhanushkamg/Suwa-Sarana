package com.suwasarana.api.ai.dto;

public class FaqAnswerDto {
    private String question;
    private String answer;
    private String locale;
    private boolean groundedInRules;
    private String disclaimer;

    public FaqAnswerDto() {
    }

    public FaqAnswerDto(String question, String answer, String locale, boolean groundedInRules, String disclaimer) {
        this.question = question;
        this.answer = answer;
        this.locale = locale;
        this.groundedInRules = groundedInRules;
        this.disclaimer = disclaimer;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public boolean isGroundedInRules() {
        return groundedInRules;
    }

    public void setGroundedInRules(boolean groundedInRules) {
        this.groundedInRules = groundedInRules;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
