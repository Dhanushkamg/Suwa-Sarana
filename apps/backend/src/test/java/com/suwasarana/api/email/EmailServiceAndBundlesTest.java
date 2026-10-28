package com.suwasarana.api.email;

import com.suwasarana.api.auth.AuthMessages;
import com.suwasarana.api.auth.AuthProperties;
import com.suwasarana.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class EmailServiceAndBundlesTest {

    private final List<EmailMessage> sent = new ArrayList<>();
    private EmailService service;
    private final AuthMessages messages = new AuthMessages();
    private AuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setFrontendUrl("https://suwasarana.lk/");
        service = new EmailService(sent::add, messages, properties, Runnable::run);
    }

    private User user(String language) {
        User u = new User();
        u.setEmail("nimal@example.com");
        u.setFirstName("Nimal");
        u.setPreferredLanguage(language);
        return u;
    }

    @Test
    void verificationMail_hasASingleUseLinkOnTheFrontend_inTheUsersLanguage() {
        service.sendEmailVerification(user("si"), "tok_en-1");
        service.sendEmailVerification(user("ta"), "tok_en-1");
        service.sendEmailVerification(user("en"), "tok_en-1");

        assertEquals(3, sent.size());
        for (EmailMessage m : sent) {
            assertEquals("nimal@example.com", m.to());
            assertTrue(m.body().contains("https://suwasarana.lk/verify-email?token=tok_en-1"), m.body());
            assertTrue(m.body().contains("24"), "tells how long the link works");
            assertTrue(m.body().contains("Nimal"));
        }
        assertTrue(sent.get(0).subject().chars().anyMatch(c -> c >= 0x0D80 && c <= 0x0DFF), "Sinhala subject");
        assertTrue(sent.get(1).subject().chars().anyMatch(c -> c >= 0x0B80 && c <= 0x0BFF), "Tamil subject");
        assertTrue(sent.get(2).subject().startsWith("Confirm"));
    }

    @Test
    void resetMail_linkAndLifetime() {
        service.sendPasswordReset(user("en"), "abc");

        assertTrue(sent.get(0).body().contains("https://suwasarana.lk/reset-password?token=abc"));
        assertTrue(sent.get(0).body().contains("30 minutes"));
    }

    @Test
    void tokensAreUrlEncoded_soAnAttackerCannotSmuggleParameters() {
        service.sendPasswordReset(user("en"), "a&b=c d");

        assertTrue(sent.get(0).body().contains("token=a%26b%3Dc%20d") || sent.get(0).body().contains("token=a%26b%3Dc+d"), sent.get(0).body());
    }

    @Test
    void inviteMail_carriesTheEmail_andFallsBackToTheAddressWhenThereIsNoName() {
        service.sendAdminInvite("new.admin@example.com", null, "en", "inv123");

        EmailMessage m = sent.get(0);
        assertEquals("new.admin@example.com", m.to());
        assertTrue(m.body().contains("https://suwasarana.lk/accept-invite?token=inv123&email=new.admin@example.com"), m.body());
        assertTrue(m.body().startsWith("Hello new.admin@example.com"));
    }

    @Test
    void decisionMail_includesTheReason() {
        service.sendVerificationDecision(user("en"), false, "Licence number could not be confirmed");

        assertTrue(sent.get(0).body().contains("Licence number could not be confirmed"));
    }

    @Test
    void aFailingSenderNeverBreaksTheCaller() {
        EmailService failing = new EmailService(m -> { throw new IllegalStateException("smtp down"); }, messages, properties, Runnable::run);

        assertDoesNotThrow(() -> failing.sendPasswordReset(user("en"), "x"));
    }

    // ---- bundles -----------------------------------------------------------------------------------------

    private Properties load(String lang) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/i18n/auth_" + lang + ".properties")) {
            assertNotNull(in, "auth_" + lang + ".properties exists");
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        }
    }

    @Test
    void allThreeLanguages_defineExactlyTheSameKeys() throws Exception {
        Set<String> en = new TreeSet<>(load("en").stringPropertyNames());
        assertEquals(en, new TreeSet<>(load("si").stringPropertyNames()), "Sinhala keys match English");
        assertEquals(en, new TreeSet<>(load("ta").stringPropertyNames()), "Tamil keys match English");
        assertTrue(en.size() >= 20);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "si", "ta"})
    void everyTextFormatsWithItsPlaceholders_andIsNotEmpty(String lang) throws Exception {
        Properties p = load(lang);
        Properties en = load("en");
        for (String key : p.stringPropertyNames()) {
            String text = p.getProperty(key);
            assertFalse(text.isBlank(), lang + ":" + key);
            // every {n} used in English must also be used in the translation, and the pattern must be valid MessageFormat
            for (int i = 0; i < 4; i++) {
                if (en.getProperty(key).contains("{" + i + "}")) {
                    assertTrue(text.contains("{" + i + "}"), lang + ":" + key + " is missing {" + i + "}");
                }
            }
            assertDoesNotThrow(() -> new MessageFormat(text, Locale.ENGLISH).format(new Object[]{"A", "B", "C", "D"}), lang + ":" + key);
        }
    }

    @Test
    void unknownLanguage_andMissingKey_fallBackSafely() {
        assertTrue(messages.get("fr", "sms.otp", "123456", 10).contains("123456"));
        assertEquals("no.such.key", messages.get("en", "no.such.key"));
        assertEquals("en", AuthMessages.languageOf(Locale.FRENCH));
        assertEquals("si", AuthMessages.languageOf(new Locale("si", "LK")));
        assertEquals("ta", AuthMessages.normalizeLanguage("TA"));
    }
}
