package com.suwasarana.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads a user's {@code preferred_language} (en / si / ta) without depending on the User entity, because the column
 * is added by the account work (migrations V25-V29) and may not exist yet. Defensive on purpose:
 * the column's existence is checked once through information_schema, so a missing column never aborts a Postgres
 * transaction, and every failure falls back to English.
 */
@Component
public class UserLanguageLookup {

    private static final Logger log = LoggerFactory.getLogger(UserLanguageLookup.class);

    private final JdbcTemplate jdbc;
    private volatile Boolean columnPresent;

    @Autowired
    public UserLanguageLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Test seam: a lookup that always answers {@code lang}. */
    public static UserLanguageLookup fixed(String lang) {
        return new UserLanguageLookup(null) {
            @Override
            public String languageOf(Long userId) {
                return lang;
            }
        };
    }

    public String languageOf(Long userId) {
        if (userId == null || jdbc == null || !columnPresent()) return NotificationTemplates.EN;
        try {
            List<String> rows = jdbc.queryForList("SELECT preferred_language FROM users WHERE id = ?", String.class, userId);
            return rows.isEmpty() ? NotificationTemplates.EN : NotificationTemplates.language(rows.get(0));
        } catch (Exception e) {
            log.debug("preferred_language lookup failed, using English: {}", e.getMessage());
            return NotificationTemplates.EN;
        }
    }

    private boolean columnPresent() {
        Boolean cached = columnPresent;
        if (cached != null) return cached;
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE LOWER(table_name) = 'users' AND LOWER(column_name) = 'preferred_language'",
                    Integer.class);
            columnPresent = n != null && n > 0;
        } catch (Exception e) {
            columnPresent = false;
        }
        return columnPresent;
    }
}
