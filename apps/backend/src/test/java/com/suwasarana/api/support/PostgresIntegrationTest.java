package com.suwasarana.api.support;

import com.suwasarana.api.notification.sms.SmsProvider;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Base class for tests that need the REAL stack: PostgreSQL 16 (Testcontainers), every Flyway migration applied,
 * Hibernate {@code ddl-auto=validate} against that schema, the full Spring Security filter chain via MockMvc.
 *
 * <p>Profile {@code pgtest} (src/test/resources/application-pgtest.yml) runs the application exactly like a
 * production profile would: strict start-up check, Flyway placeholder {@code environment=prod} (so V22 removes the
 * demo data), JSON-free test logging. Subclasses share ONE Spring context and ONE database; give your test data unique
 * values (random e-mail / phone numbers) instead of assuming an empty database.</p>
 *
 * <p>Skipped automatically when Docker is unavailable (see {@link DockerAvailableCondition}); CI requires it.</p>
 */
@SpringBootTest
@ActiveProfiles("pgtest")
@AutoConfigureMockMvc
@ExtendWith(DockerAvailableCondition.class)
public abstract class PostgresIntegrationTest {

    /**
     * Spy on the SMS gateway so tests can read the OTP that would have been texted, with whatever OTP storage the
     * application uses (plain, hashed...). Declared here so all subclasses share one Spring context.
     */
    @MockitoSpyBean
    protected SmsProvider smsProvider;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgres::jdbcUrl);
        registry.add("spring.datasource.username", SharedPostgres::username);
        registry.add("spring.datasource.password", SharedPostgres::password);
    }
}
