package com.suwasarana.api.support;

import com.jayway.jsonpath.JsonPath;
import com.suwasarana.api.notification.sms.SmsProvider;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    // ------------------------------------------------------------------------------------------------ helpers

    protected static final String TEST_PASSWORD = "Str0ng-Passw0rd!";
    private static final Pattern OTP = Pattern.compile("(?<![0-9])([0-9]{6})(?![0-9])");
    private static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @Autowired
    protected MockMvc mockMvc;

    /** Unique 10-digit local number per call so the shared database never collides between tests. */
    protected static String uniquePhone() {
        long n = Math.abs(UUID.randomUUID().getMostSignificantBits()) % 90_000_000L + 10_000_000L;
        return "07" + n;
    }

    /** A different client address per call: the auth endpoints are rate limited per IP. */
    protected static RequestPostProcessor freshClientIp() {
        int n = IP_SEQ.getAndIncrement();
        return request -> {
            request.setRemoteAddr("10.77." + (n / 250) + "." + (n % 250 + 1));
            return request;
        };
    }

    /**
     * Registers a user through the real OTP flow (the code is read from the SMS handed to the spied gateway, so this
     * works however OTPs are stored) and returns {accessToken, userId, email, phone, refreshCookieHeader}.
     */
    protected String[] registerViaOtp(String role, String phone) throws Exception {
        String email = role.toLowerCase() + "." + UUID.randomUUID().toString().substring(0, 8) + "@example.org";

        mockMvc.perform(post("/api/auth/send-otp").with(freshClientIp())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phoneNumber\":\"" + phone + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(smsProvider, atLeastOnce()).sendSms(eq(phone), message.capture());
        Matcher m = OTP.matcher(message.getValue());
        assertThat(m.find()).as("the SMS contains a 6-digit code").isTrue();
        String otp = m.group(1);

        mockMvc.perform(post("/api/auth/verify-otp").with(freshClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\",\"otpCode\":\"" + otp + "\"}"))
                .andExpect(status().isOk());

        var result = mockMvc.perform(post("/api/auth/register").with(freshClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"phoneNumber\":\"" + phone + "\",\"password\":\"" + TEST_PASSWORD
                                + "\",\"firstName\":\"E2E\",\"lastName\":\"" + role + "\",\"role\":\"" + role + "\",\"otpCode\":\"" + otp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse();
        String response = result.getContentAsString();
        return new String[] {JsonPath.read(response, "$.data.accessToken"),
                String.valueOf((Object) JsonPath.read(response, "$.data.userId")), email, phone,
                result.getHeader("Set-Cookie")};
    }
}
