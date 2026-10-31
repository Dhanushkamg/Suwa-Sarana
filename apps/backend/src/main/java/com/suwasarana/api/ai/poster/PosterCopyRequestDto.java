package com.suwasarana.api.ai.poster;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request of {@code POST /api/ai/poster-copy}.
 * {@code facts} may contain only: bloodType, units, hospital, district, date (yyyy-MM-dd), time (HH:mm), venue,
 * contactPhone, campName, organiser. Any patient-name field is refused (422 poster.patient_name_not_allowed).
 */
public class PosterCopyRequestDto {

    @NotBlank(message = "template is required")
    @Pattern(regexp = "URGENT_REQUEST|CAMP|AWARENESS|THANK_YOU", message = "template must be URGENT_REQUEST, CAMP, AWARENESS or THANK_YOU")
    private String template;

    @NotBlank(message = "language is required")
    @Pattern(regexp = "en|si|ta", message = "language must be en, si or ta")
    private String language;

    @NotNull(message = "facts are required")
    @Size(max = 20, message = "too many facts")
    private Map<String, String> facts = new LinkedHashMap<>();

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Map<String, String> getFacts() { return facts; }
    public void setFacts(Map<String, String> facts) { this.facts = facts; }
}
