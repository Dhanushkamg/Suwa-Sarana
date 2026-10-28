package com.suwasarana.api.auth;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Texts the server itself sends (SMS, email): looked up in {@code i18n/auth_<lang>.properties} (UTF-8) for the
 * recipient's preferred language (en | si | ta), falling back to English. API error messages are NOT here:
 * the API returns codes only and the frontend translates them.
 */
@Component
public class AuthMessages {

    public static final String DEFAULT_LANGUAGE = "en";
    private static final java.util.Set<String> SUPPORTED = java.util.Set.of("en", "si", "ta");

    private final Map<String, Properties> bundles = new ConcurrentHashMap<>();

    /** "si", "si-LK", Locale("ta") ... -> one of en|si|ta. */
    public static String languageOf(Locale locale) {
        if (locale == null) {
            return DEFAULT_LANGUAGE;
        }
        return normalizeLanguage(locale.getLanguage());
    }

    public static String normalizeLanguage(String language) {
        if (language == null) {
            return DEFAULT_LANGUAGE;
        }
        String l = language.trim().toLowerCase(Locale.ROOT);
        if (l.length() > 2) {
            l = l.substring(0, 2);
        }
        return SUPPORTED.contains(l) ? l : DEFAULT_LANGUAGE;
    }

    public static boolean isSupported(String language) {
        return language != null && SUPPORTED.contains(language);
    }

    /** The text for {@code key} with {0}, {1}... filled in (java.text.MessageFormat). */
    public String get(String language, String key, Object... args) {
        String lang = normalizeLanguage(language);
        String pattern = bundle(lang).getProperty(key);
        if (pattern == null && !DEFAULT_LANGUAGE.equals(lang)) {
            pattern = bundle(DEFAULT_LANGUAGE).getProperty(key);
        }
        if (pattern == null) {
            return key;
        }
        return args == null || args.length == 0 ? pattern : new MessageFormat(pattern, Locale.ENGLISH).format(args);
    }

    private Properties bundle(String lang) {
        return bundles.computeIfAbsent(lang, l -> {
            Properties p = new Properties();
            try (InputStream in = AuthMessages.class.getResourceAsStream("/i18n/auth_" + l + ".properties")) {
                if (in != null) {
                    p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read i18n/auth_" + l + ".properties", e);
            }
            return p;
        });
    }
}
