package com.suwasarana.api.circle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * One-off conversion of circles that were created before V40: the legacy plaintext {@code invite_token} is
 * encrypted into {@code invite_token_enc} and then erased. Idempotent, safe to run on every start and on several
 * instances at once. Does nothing (and logs nothing alarming) when the legacy column does not exist.
 */
@Component
public class CircleTokenMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CircleTokenMigrator.class);

    private final JdbcTemplate jdbc;
    private final CircleTokens tokens;

    @Autowired
    public CircleTokenMigrator(JdbcTemplate jdbc, CircleTokens tokens) {
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int converted = convertLegacyTokens();
            if (converted > 0) {
                log.info("Converted {} legacy plaintext circle invite token(s) to hashed + encrypted form", converted);
            }
        } catch (DataAccessException e) {
            // No legacy column (fresh H2 schema, or the column was dropped already): nothing to convert.
            log.debug("Circle token conversion skipped: {}", e.getClass().getSimpleName());
        }
    }

    /** @return number of rows converted */
    int convertLegacyTokens() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, invite_token FROM request_circles WHERE invite_token IS NOT NULL");
        int converted = 0;
        for (Map<String, Object> row : rows) {
            Number id = (Number) row.get("id");
            String token = (String) row.get("invite_token");
            int updated = jdbc.update(
                    "UPDATE request_circles SET invite_token_hash = ?, invite_token_enc = ?, invite_token = NULL "
                            + "WHERE id = ? AND invite_token IS NOT NULL",
                    tokens.hash(token), tokens.encrypt(token), id.longValue());
            converted += updated;
        }
        return converted;
    }
}
