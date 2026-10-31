package com.suwasarana.api.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Request of {@code POST /api/faq/ask}.
 * <ul>
 *   <li>{@code question}: required, at most 500 characters.</li>
 *   <li>{@code history}: optional earlier turns, oldest first; the server uses only the last 6 and caps every message.</li>
 *   <li>{@code language}: reply language {@code en|si|ta|auto} (default auto = answer in the language of the question).</li>
 *   <li>{@code replyStyle}: {@code auto|script|romanised}; only matters for Singlish questions.</li>
 *   <li>{@code locale}: DEPRECATED alias kept for the old frontend. It is the UI language and is used only as a hint
 *       when the language of the question cannot be detected (for example a message of digits).</li>
 * </ul>
 */
public class FaqQuestionDto {

    @NotBlank(message = "Question cannot be blank")
    @Size(max = 500, message = "Question must be at most 500 characters")
    private String question;

    @Size(max = 20, message = "History can hold at most 20 messages")
    @Valid
    private List<Turn> history = new ArrayList<>();

    @Pattern(regexp = "(?i)en|si|ta|auto", message = "language must be en, si, ta or auto")
    private String language;

    @Pattern(regexp = "(?i)auto|script|romanised|romanized", message = "replyStyle must be auto, script or romanised")
    private String replyStyle;

    /** Deprecated: UI language hint. */
    private String locale;

    public FaqQuestionDto() {
    }

    public FaqQuestionDto(String question, String locale) {
        this.question = question;
        this.locale = locale;
    }

    /** One earlier message of the conversation. */
    public static class Turn {
        @Pattern(regexp = "(?i)user|assistant", message = "role must be user or assistant")
        private String role;
        @Size(max = 2000, message = "A history message can hold at most 2000 characters")
        private String content;

        public Turn() {
        }

        public Turn(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<Turn> getHistory() { return history; }
    public void setHistory(List<Turn> history) { this.history = history == null ? new ArrayList<>() : history; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getReplyStyle() { return replyStyle; }
    public void setReplyStyle(String replyStyle) { this.replyStyle = replyStyle; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
}
