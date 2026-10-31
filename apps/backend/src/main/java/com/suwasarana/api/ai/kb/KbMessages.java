package com.suwasarana.api.ai.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fixed, localised chatbot messages ({@code kb/messages.json}) with contact placeholders resolved. */
@Component
public class KbMessages {

    public static final String RESOURCE = "kb/messages.json";
    private final Map<String, Map<String, String>> messages;

    public KbMessages(KnowledgeBase kb) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        try (InputStream in = KbMessages.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing resource " + RESOURCE);
            JsonNode root = new ObjectMapper().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            root.path("messages").fields().forEachRemaining(e -> {
                Map<String, String> perLang = new LinkedHashMap<>();
                for (String lang : KnowledgeBase.LANGS) {
                    String raw = e.getValue().path(lang).asText("");
                    perLang.put(lang, kb.resolve(raw, "message:" + e.getKey(), lang));
                }
                out.put(e.getKey(), Collections.unmodifiableMap(perLang));
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + RESOURCE, ex);
        }
        this.messages = Collections.unmodifiableMap(out);
    }

    /** Message in the given language; falls back to English; throws for unknown keys (programming error). */
    public String get(String key, String lang) {
        Map<String, String> m = messages.get(key);
        if (m == null) throw new IllegalArgumentException("Unknown message key " + key);
        String v = m.get(lang);
        return v == null || v.isBlank() ? m.get("en") : v;
    }

    public boolean has(String key) {
        return messages.containsKey(key);
    }
}
