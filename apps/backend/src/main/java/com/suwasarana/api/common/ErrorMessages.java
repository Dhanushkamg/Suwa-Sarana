package com.suwasarana.api.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * English fallback text for an error code ({@code i18n/error_messages_en.properties}). The API contract is:
 * {@code code} = machine key the frontend translates, {@code message} = human English fallback (this text, or the
 * thrower's own more specific sentence). Never user input.
 */
public final class ErrorMessages {

    private static final Properties EN = load();

    private ErrorMessages() {}

    /** English text for {@code code}; the code itself when unknown (never null). */
    public static String english(String code) {
        if (code == null) {
            return "An unexpected error occurred";
        }
        return EN.getProperty(code, code);
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = ErrorMessages.class.getResourceAsStream("/i18n/error_messages_en.properties")) {
            if (in != null) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read i18n/error_messages_en.properties", e);
        }
        return p;
    }
}
