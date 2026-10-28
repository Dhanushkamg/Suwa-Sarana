package com.suwasarana.api.auth;

import com.suwasarana.api.auth.otp.OtpVerificationRepository;
import com.suwasarana.api.support.IntegrationTestBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registration, OTP, login, lockout, password reset, email verification and Google sign-in through the real HTTP API. */
class AuthFlowIntegrationTest extends IntegrationTestBase {

    @Autowired private OtpVerificationRepository otpRepository;
    @Autowired private com.suwasarana.api.auth.RefreshTokenRepository refreshTokenRepository;

    private Map<String, Object> registration(String email, String phone, Role role) {
        return new java.util.HashMap<>(Map.of(
                "email", email, "phoneNumber", phone, "password", STRONG_PASSWORD, "firstName", "Nimal", "lastName", "Perera",
                "role", role.name(), "acceptTerms", true));
    }

    // ---- registration: validation ---------------------------------------------------------------------------

    @Test
    void registration_returnsFieldLevelCodes_notAGenericBanner() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("email", "not-an-email");
        body.put("phoneNumber", "12345");
        body.put("password", "short");
        body.put("firstName", "");
        body.put("role", "DONOR");
        body.put("acceptTerms", false);

        postJson("/api/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.email").value(AuthCodes.EMAIL_INVALID))
                .andExpect(jsonPath("$.data.phoneNumber").value(AuthCodes.PHONE_INVALID))
                .andExpect(jsonPath("$.data.password").value(AuthCodes.PASSWORD_TOO_SHORT))
                .andExpect(jsonPath("$.data.firstName").value(AuthCodes.FIRST_NAME_REQUIRED))
                .andExpect(jsonPath("$.data.lastName").value(AuthCodes.LAST_NAME_REQUIRED))
                .andExpect(jsonPath("$.data.acceptTerms").value(AuthCodes.CONSENT_REQUIRED));
    }

    @Test
    void registration_withoutConsent_isRefused() throws Exception {
        Map<String, Object> body = registration(uniqueEmail("noconsent"), uniquePhone(), Role.DONOR);
        body.remove("acceptTerms");

        postJson("/api/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.acceptTerms").value(AuthCodes.CONSENT_REQUIRED));
    }

    @Test
    void registration_enforcesThePasswordPolicy() throws Exception {
        String phone = uniquePhone();
        verifyPhone(phone);
        Map<String, Object> body = registration(uniqueEmail("weak"), phone, Role.DONOR);
        body.put("password", "password1234");

        postJson("/api/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.password").value(AuthCodes.PASSWORD_COMMON));

        body.put("password", body.get("email"));
        postJson("/api/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.password").value(AuthCodes.PASSWORD_SAME_AS_IDENTIFIER));
    }

    @Test
    void registration_cannotCreateAnAdmin() throws Exception {
        String phone = uniquePhone();
        verifyPhone(phone);

        postJson("/api/auth/register", registration(uniqueEmail("evil"), phone, Role.ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.role").value(AuthCodes.ROLE_NOT_ALLOWED));
    }

    @Test
    void registration_requiresAPhoneProvedByOtp() throws Exception {
        postJson("/api/auth/register", registration(uniqueEmail("noverify"), uniquePhone(), Role.DONOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.phoneNumber").value(AuthCodes.PHONE_NOT_VERIFIED));
    }

    // ---- registration: success + normalisation ----------------------------------------------------------------

    @Test
    void registration_storesE164_recordsConsent_andSendsALocalisedVerificationMail() throws Exception {
        String phone = uniquePhone();
        String local = "0" + phone.substring(3);      // the user types the local spelling
        String email = uniqueEmail("Nimal.Mixed.Case").toUpperCase().replace("@EXAMPLE.COM", "@example.com");
        verifyPhone(local);
        Map<String, Object> body = registration(email, local, Role.HOSPITAL_REQUESTER);
        body.put("preferredLanguage", "si");

        MvcResult result = postJson("/api/auth/register", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneNumber").value(phone))
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andExpect(jsonPath("$.data.preferredLanguage").value("si"))
                .andReturn();

        User user = userRepository.findById(Long.valueOf(jsonOf(result, "$.data.userId"))).orElseThrow();
        assertEquals(phone, user.getPhoneNumber());
        assertEquals(VerificationStatus.PENDING, user.getVerificationStatus());
        assertEquals("2026-01", user.getConsentVersion());
        assertNotNull(user.getConsentedAt());
        assertNotNull(result.getResponse().getCookie("refresh_token"));
        assertTrue(result.getResponse().getHeader("Set-Cookie").contains("HttpOnly"));

        var mail = outbox.lastEmailTo(email.toLowerCase());
        assertNotNull(mail, "verification mail sent");
        assertTrue(mail.body().contains("/verify-email?token="));
        assertTrue(mail.subject().chars().anyMatch(c -> c >= 0x0D80 && c <= 0x0DFF), "in Sinhala: " + mail.subject());
        assertEquals(0, otpRepository.findAll().stream().filter(o -> o.getPhoneNumber().equals(phone)).count(),
                "the OTP record is consumed by registration");
    }

    @Test
    void registration_sameAccountTwice_isTheSameNeutral409_andTheOwnerIsToldByEmail() throws Exception {
        Session first = registerUser(Role.DONOR);
        outbox.emails.clear();

        // same email, brand-new verified phone
        String phone2 = uniquePhone();
        verifyPhone(phone2);
        postJson("/api/auth/register", registration(first.email, phone2, Role.DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(AuthCodes.ACCOUNT_EXISTS));
        assertNotNull(outbox.lastEmailTo(first.email), "the real owner is warned");

        // same phone, brand-new email
        verifyPhone(first.phone);
        postJson("/api/auth/register", registration(uniqueEmail("other"), first.phone, Role.DONOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(AuthCodes.ACCOUNT_EXISTS));
    }

    @Test
    void registration_concurrentDuplicates_yieldOne200AndOnlyConflicts_never500() throws Exception {
        String phone = uniquePhone();
        String email = uniqueEmail("race");
        verifyPhone(phone);
        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return postJson("/api/auth/register", registration(email, phone, Role.DONOR)).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<Integer> f : results) {
            int code = f.get();
            assertNotEquals(500, code, "a lost race must be a 409");
            assertTrue(code == 200 || code == 409, "unexpected status " + code);
            if (code == 200) {
                ok++;
            }
        }
        pool.shutdownNow();
        assertEquals(1, ok, "exactly one sign-up wins");
        assertEquals(1, userRepository.findAll().stream().filter(u -> u.getEmail().equals(email)).count());
    }

    @Test
    void nic_blankIsNull_duplicatesAre409_andNothingIsStoredInClear() throws Exception {
        for (int i = 0; i < 2; i++) { // two users with a blank NIC must both succeed (the old code stored "" twice -> 500)
            String phone = uniquePhone();
            verifyPhone(phone);
            Map<String, Object> body = registration(uniqueEmail("blank"), phone, Role.DONOR);
            body.put("nicNumber", "");
            postJson("/api/auth/register", body).andExpect(status().isOk());
        }

        String nic = "19901234" + (4000 + (int) (Math.random() * 999));
        String phoneA = uniquePhone();
        verifyPhone(phoneA);
        Map<String, Object> a = registration(uniqueEmail("nica"), phoneA, Role.DONOR);
        a.put("nicNumber", nic);
        MvcResult ok = postJson("/api/auth/register", a).andExpect(status().isOk()).andReturn();
        User stored = userRepository.findById(Long.valueOf(jsonOf(ok, "$.data.userId"))).orElseThrow();
        assertNull(stored.getNicNumber(), "no clear-text NIC");
        assertNotNull(stored.getNicHash());
        assertFalse(stored.getNicHash().contains(nic));

        String phoneB = uniquePhone();
        verifyPhone(phoneB);
        Map<String, Object> b = registration(uniqueEmail("nicb"), phoneB, Role.DONOR);
        b.put("nicNumber", nic);
        postJson("/api/auth/register", b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(AuthCodes.NIC_IN_USE));

        b.put("nicNumber", "not-a-nic");
        postJson("/api/auth/register", b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.nicNumber").value(AuthCodes.NIC_INVALID));
    }

    // ---- OTP -------------------------------------------------------------------------------------------------

    @Test
    void otp_isHashedAtRest_resendIsRateLimited_andFailuresAreReported() throws Exception {
        String phone = uniquePhone();
        postJson("/api/auth/send-otp", Map.of("phoneNumber", phone)).andExpect(status().isOk());
        String code = lastOtpCode(phone);

        var rows = otpRepository.findAll().stream().filter(o -> o.getPhoneNumber().equals(phone)).toList();
        assertEquals(1, rows.size());
        assertNotEquals(code, rows.get(0).getOtpCode());
        assertEquals(64, rows.get(0).getOtpCode().length());

        MvcResult again = postJson("/api/auth/send-otp", Map.of("phoneNumber", phone))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(AuthCodes.OTP_COOLDOWN))
                .andReturn();
        assertNotNull(again.getResponse().getHeader("Retry-After"));

        postJson("/api/auth/verify-otp", Map.of("phoneNumber", phone, "otpCode", "000000"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(AuthCodes.OTP_INVALID));
        postJson("/api/auth/verify-otp", Map.of("phoneNumber", "0" + phone.substring(3), "otpCode", code)).andExpect(status().isOk());

        // an SMS gateway failure is reported and does not start the cooldown
        String phone2 = uniquePhone();
        outbox.smsWorks = false;
        postJson("/api/auth/send-otp", Map.of("phoneNumber", phone2))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value(AuthCodes.SMS_FAILED));
        outbox.smsWorks = true;
        postJson("/api/auth/send-otp", Map.of("phoneNumber", phone2)).andExpect(status().isOk());
    }

    // ---- login + lockout --------------------------------------------------------------------------------------

    @Test
    void login_worksByEmailAndByEveryPhoneSpelling() throws Exception {
        Session s = registerUser(Role.DONOR);

        login(s.email, s.password);
        login(s.email.toUpperCase(), s.password);
        login(s.phone, s.password);
        login("0" + s.phone.substring(3), s.password);
        login(s.phone.substring(1), s.password);
    }

    @Test
    void login_wrongCredentials_areNeutral401() throws Exception {
        Session s = registerUser(Role.DONOR);

        postJson("/api/auth/login", Map.of("username", s.email, "password", "wrong-password-1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(AuthCodes.CREDENTIALS_INVALID));
        postJson("/api/auth/login", Map.of("username", "nobody@example.com", "password", "wrong-password-1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(AuthCodes.CREDENTIALS_INVALID));
    }

    @Test
    void lockout_afterFiveFailures_isPerAccount_notPerIp_andClearedByAPasswordReset() throws Exception {
        Session s = registerUser(Role.DONOR);

        for (int i = 0; i < 5; i++) { // every request comes from a different IP
            postJson("/api/auth/login", Map.of("username", s.email, "password", "wrong-password-" + i))
                    .andExpect(status().isUnauthorized());
        }
        MvcResult locked = postJson("/api/auth/login", Map.of("username", s.email, "password", s.password))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(AuthCodes.ACCOUNT_LOCKED))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber())
                .andReturn();
        int retry = Integer.parseInt(locked.getResponse().getHeader("Retry-After"));
        assertTrue(retry > 0 && retry <= 61, "retry-after " + retry);
        assertTrue(userRepository.findById(s.userId).orElseThrow().getLockedUntil().isAfter(Instant.now()));

        // a phone-OTP password reset unlocks the account
        verifyPhone(s.phone);
        postJson("/api/auth/reset-password", Map.of("phoneNumber", s.phone, "newPassword", "Brand-New-Passphrase-77"))
                .andExpect(status().isOk());
        User after = userRepository.findById(s.userId).orElseThrow();
        assertEquals(0, after.getFailedLoginCount());
        assertNull(after.getLockedUntil());
        login(s.email, "Brand-New-Passphrase-77");
    }

    @Test
    void lockout_expires_andASuccessfulLoginResetsTheCounter() throws Exception {
        Session s = registerUser(Role.DONOR);
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/login", Map.of("username", s.email, "password", "wrong-password-" + i)).andExpect(status().isUnauthorized());
        }
        User u = userRepository.findById(s.userId).orElseThrow();
        userRepository.setLockedUntil(u.getId(), Instant.now().minusSeconds(1)); // time passes

        login(s.email, s.password);

        User after = userRepository.findById(s.userId).orElseThrow();
        assertEquals(0, after.getFailedLoginCount());
        assertNull(after.getLockedUntil());
    }

    // ---- password reset (F-02) --------------------------------------------------------------------------------

    @Test
    void phoneReset_neverRevivesDisabledOrDemoAccounts() throws Exception {
        Session s = registerUser(Role.DONOR);
        User u = userRepository.findById(s.userId).orElseThrow();

        u.setDemoAccount(true);
        userRepository.save(u);
        verifyPhone(s.phone);
        postJson("/api/auth/reset-password", Map.of("phoneNumber", s.phone, "newPassword", "Brand-New-Passphrase-77"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(AuthCodes.RESET_NOT_ALLOWED));

        u = userRepository.findById(s.userId).orElseThrow();
        u.setDemoAccount(false);
        u.setPasswordHash("!disabled-demo-account");
        userRepository.save(u);
        postJson("/api/auth/reset-password", Map.of("phoneNumber", s.phone, "newPassword", "Brand-New-Passphrase-77"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(AuthCodes.RESET_NOT_ALLOWED));
        assertEquals("!disabled-demo-account", userRepository.findById(s.userId).orElseThrow().getPasswordHash());
    }

    @Test
    void emailReset_fullFlow_singleUse_revokesSessions_andKillsOldAccessTokens() throws Exception {
        Session s = registerUser(Role.DONOR);
        String oldAccess = s.accessToken;
        getJson("/api/users/me", oldAccess).andExpect(status().isOk());
        outbox.emails.clear();

        // unknown address: 200 and silence
        postJson("/api/auth/forgot-password", Map.of("email", "ghost@example.com")).andExpect(status().isOk());
        assertTrue(outbox.emails.isEmpty());

        postJson("/api/auth/forgot-password", Map.of("email", s.email)).andExpect(status().isOk());
        String token = tokenFromLastEmail(s.email);
        assertTrue(outbox.lastEmailTo(s.email).body().contains("/reset-password?token="));

        // too weak: refused AND the link is still good
        postJson("/api/auth/reset-password/token", Map.of("token", token, "newPassword", "password1234"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.newPassword").value(AuthCodes.PASSWORD_COMMON));

        postJson("/api/auth/reset-password/token", Map.of("token", token, "newPassword", "Another-Strong-Passphrase-9"))
                .andExpect(status().isOk());

        // single use
        postJson("/api/auth/reset-password/token", Map.of("token", token, "newPassword", "Yet-Another-Passphrase-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(AuthCodes.RESET_TOKEN_INVALID));

        // old password dead, new one works
        postJson("/api/auth/login", Map.of("username", s.email, "password", s.password)).andExpect(status().isUnauthorized());
        login(s.email, "Another-Strong-Passphrase-9");
        // every old session is dead: access token and refresh cookie
        getJson("/api/users/me", oldAccess).andExpect(status().isUnauthorized());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, s.refreshCookie)
                .andExpect(status().isUnauthorized());
        // reaching the inbox proved the address
        assertTrue(userRepository.findById(s.userId).orElseThrow().isEmailVerified());
    }

    @Test
    void emailReset_isNotOfferedToDemoAccounts() throws Exception {
        Session s = registerUser(Role.DONOR);
        User u = userRepository.findById(s.userId).orElseThrow();
        u.setDemoAccount(true);
        userRepository.save(u);
        outbox.emails.clear();

        postJson("/api/auth/forgot-password", Map.of("email", s.email)).andExpect(status().isOk());

        assertEquals(0, outbox.emailsTo(s.email));
    }

    @Test
    void emailReset_expiredTokensAreRefused() throws Exception {
        Session s = registerUser(Role.DONOR);
        postJson("/api/auth/forgot-password", Map.of("email", s.email)).andExpect(status().isOk());
        String token = tokenFromLastEmail(s.email);
        expireOpenTokens(s.userId);

        postJson("/api/auth/reset-password/token", Map.of("token", token, "newPassword", "Another-Strong-Passphrase-9"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(AuthCodes.RESET_TOKEN_INVALID));
    }

    @Autowired private com.suwasarana.api.auth.token.AuthTokenRepository authTokenRepository;

    private void expireOpenTokens(Long userId) {
        authTokenRepository.findAll().stream().filter(t -> userId.equals(t.getUserId())).forEach(t -> {
            t.setExpiresAt(Instant.now().minusSeconds(5));
            authTokenRepository.save(t);
        });
    }

    // ---- email verification -------------------------------------------------------------------------------------

    @Test
    void emailVerification_singleUse_withResendCooldown() throws Exception {
        Session s = registerUser(Role.HOSPITAL_REQUESTER);
        String token = tokenFromLastEmail(s.email);
        getJson("/api/users/me", s.accessToken).andExpect(jsonPath("$.data.emailVerified").value(false));

        // resend: needs a session, and is rate-limited
        postJson("/api/auth/resend-verification", null).andExpect(status().isUnauthorized());
        postJson("/api/auth/resend-verification", null, s.accessToken)
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(AuthCodes.EMAIL_VERIFY_COOLDOWN));

        postJson("/api/auth/verify-email", Map.of("token", "garbage")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthCodes.EMAIL_VERIFY_TOKEN_INVALID));
        postJson("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isOk());
        postJson("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isBadRequest());

        getJson("/api/users/me", s.accessToken).andExpect(jsonPath("$.data.emailVerified").value(true));
        postJson("/api/auth/resend-verification", null, s.accessToken)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(AuthCodes.EMAIL_ALREADY_VERIFIED));
    }

    @Test
    void emailVerification_tokenIsStoredHashed() throws Exception {
        Session s = registerUser(Role.DONOR);
        String token = tokenFromLastEmail(s.email);

        assertTrue(authTokenRepository.findAll().stream().noneMatch(t -> t.getTokenHash().contains(token)));
        assertTrue(authTokenRepository.findByTokenHash(com.suwasarana.api.auth.token.AuthTokenService.sha256Hex(token)).isPresent());
    }

    // ---- sessions -----------------------------------------------------------------------------------------------

    @Test
    void refresh_rotates_andReuseOfAnOldTokenRevokesEverySession() throws Exception {
        Session s = registerUser(Role.DONOR);
        authProperties.setRefreshReuseGraceSeconds(0);
        try {
            MvcResult first = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, s.refreshCookie)
                    .andExpect(status().isOk()).andReturn();
            Cookie second = first.getResponse().getCookie("refresh_token");
            String access2 = jsonOf(first, "$.data.accessToken");
            assertNotEquals(s.refreshCookie.getValue(), second.getValue());
            getJson("/api/users/me", access2).andExpect(status().isOk());

            Thread.sleep(1100); // outside the grace window
            // the OLD cookie shows up again: theft
            send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, s.refreshCookie)
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(AuthCodes.SESSION_EXPIRED));

            // ... so the legitimate holder's newer cookie and access token are gone too
            send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, second)
                    .andExpect(status().isUnauthorized());
            getJson("/api/users/me", access2).andExpect(status().isUnauthorized());
            assertEquals(0, refreshTokenRepository.countByUser(userRepository.findById(s.userId).orElseThrow()));
        } finally {
            authProperties.setRefreshReuseGraceSeconds(5);
        }
    }

    @Autowired private AuthProperties authProperties;

    @Test
    void refresh_reuseInsideTheGraceWindow_isRefusedButDoesNotLogEveryoneOut() throws Exception {
        Session s = registerUser(Role.DONOR);
        MvcResult first = send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, s.refreshCookie)
                .andExpect(status().isOk()).andReturn();
        Cookie second = first.getResponse().getCookie("refresh_token");

        // a second browser tab raced with the same cookie a moment later
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, s.refreshCookie)
                .andExpect(status().isUnauthorized());

        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, second)
                .andExpect(status().isOk());
    }

    @Test
    void cookieEndpoints_refuseForeignOrigins() throws Exception {
        Session s = registerUser(Role.DONOR);

        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh").header("Origin", "https://evil.example"), null, null, s.refreshCookie)
                .andExpect(status().isForbidden()); // already stopped by the CORS filter
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh").header("Sec-Fetch-Site", "cross-site"), null, null, s.refreshCookie)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(AuthCodes.ORIGIN_BLOCKED));
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout").header("Origin", "https://evil.example"), null, null, s.refreshCookie)
                .andExpect(status().isForbidden());

        // the session survived the forged requests
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh").header("Origin", "http://localhost:3000"), null, null, s.refreshCookie)
                .andExpect(status().isOk());
    }

    @Test
    void logout_withAnExpiredAccessToken_stillEndsTheCookieSession() throws Exception {
        Session s = registerUser(Role.DONOR);

        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout"), null, null, s.refreshCookie)
                .andExpect(status().isOk());

        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, s.refreshCookie)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withAValidToken_signsOutEverywhere_andOldAccessTokensStop() throws Exception {
        Session s = registerUser(Role.DONOR);
        Session other = login(s.email, s.password);

        postJson("/api/auth/logout", null, s.accessToken).andExpect(status().isOk());

        getJson("/api/users/me", s.accessToken).andExpect(status().isUnauthorized());
        getJson("/api/users/me", other.accessToken).andExpect(status().isUnauthorized());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, other.refreshCookie)
                .andExpect(status().isUnauthorized());
    }

    // ---- Google ---------------------------------------------------------------------------------------------------

    private String googleToken(String sub, String email) {
        return "google:" + sub + ":" + email;
    }

    @Test
    void google_firstTimeUser_registersWithRolePhoneOtpAndConsent_inE164() throws Exception {
        String email = uniqueEmail("gnew");
        String token = googleToken("sub-" + email, email);

        postJson("/api/auth/google/verify", Map.of("idToken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresRegistration").value(true))
                .andExpect(jsonPath("$.data.email").value(email));
        postJson("/api/auth/google/verify", Map.of("idToken", "forged"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(AuthCodes.GOOGLE_INVALID));

        String phone = uniquePhone();
        String local = "0" + phone.substring(3);
        verifyPhone(local);
        Map<String, Object> reg = new java.util.HashMap<>(Map.of("idToken", token, "phoneNumber", local, "firstName", "Gayan",
                "lastName", "Silva", "role", "DONOR"));
        postJson("/api/auth/google/register", reg).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.acceptTerms").value(AuthCodes.CONSENT_REQUIRED));
        reg.put("acceptTerms", true);
        MvcResult done = postJson("/api/auth/google/register", reg)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneNumber").value(phone))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andReturn();

        User u = userRepository.findById(Long.valueOf(jsonOf(done, "$.data.userId"))).orElseThrow();
        assertEquals(com.suwasarana.api.user.AuthProvider.GOOGLE, u.getAuthProvider());
        assertNull(u.getPasswordHash());
        assertNotNull(u.getConsentedAt());

        // next visit signs in directly
        postJson("/api/auth/google/verify", Map.of("idToken", token))
                .andExpect(jsonPath("$.data.requiresRegistration").value(false))
                .andExpect(jsonPath("$.data.authResponse.accessToken").isString());
    }

    @Test
    void google_neverAutoLinksIntoAnUnverifiedLocalAccount_itTakesTheAccountOverAndDropsThePassword() throws Exception {
        Session squatter = registerUser(Role.DONOR); // someone registered the victim's address with their own password
        Session squatterSecond = login(squatter.email, squatter.password);
        assertFalse(userRepository.findById(squatter.userId).orElseThrow().isEmailVerified());

        MvcResult result = postJson("/api/auth/google/verify", Map.of("idToken", googleToken("sub-real-owner", squatter.email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresRegistration").value(false))
                .andExpect(jsonPath("$.data.authResponse.accessToken").isString())
                .andReturn();
        String ownerAccess = jsonOf(result, "$.data.authResponse.accessToken");

        User u = userRepository.findById(squatter.userId).orElseThrow();
        assertNull(u.getPasswordHash(), "the squatter's password is gone");
        assertEquals(com.suwasarana.api.user.AuthProvider.GOOGLE, u.getAuthProvider());
        assertEquals("sub-real-owner", u.getProviderId());
        assertTrue(u.isEmailVerified());

        postJson("/api/auth/login", Map.of("username", squatter.email, "password", squatter.password)).andExpect(status().isUnauthorized());
        getJson("/api/users/me", squatter.accessToken).andExpect(status().isUnauthorized());
        getJson("/api/users/me", squatterSecond.accessToken).andExpect(status().isUnauthorized());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, squatter.refreshCookie)
                .andExpect(status().isUnauthorized());
        getJson("/api/users/me", ownerAccess).andExpect(status().isOk());

        // and the squatter cannot get back in through the phone-OTP reset: there is no password account any more
        verifyPhone(squatter.phone);
        postJson("/api/auth/reset-password", Map.of("phoneNumber", squatter.phone, "newPassword", "Brand-New-Passphrase-77"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(AuthCodes.RESET_NOT_ALLOWED));
    }

    @Test
    void google_verifiedLocalAccount_requiresAnExplicitLinkWithThePassword() throws Exception {
        Session s = registerUser(Role.DONOR);
        postJson("/api/auth/verify-email", Map.of("token", tokenFromLastEmail(s.email))).andExpect(status().isOk());
        String token = googleToken("sub-linked", s.email);

        postJson("/api/auth/google/verify", Map.of("idToken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresLinking").value(true))
                .andExpect(jsonPath("$.data.authResponse").doesNotExist());
        assertNull(userRepository.findById(s.userId).orElseThrow().getProviderId(), "nothing was linked silently");

        postJson("/api/auth/google/link", Map.of("idToken", token, "password", "wrong-password-1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(AuthCodes.CREDENTIALS_INVALID));
        postJson("/api/auth/google/link", Map.of("idToken", token, "password", s.password))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isString());

        assertEquals("sub-linked", userRepository.findById(s.userId).orElseThrow().getProviderId());
        postJson("/api/auth/google/verify", Map.of("idToken", token))
                .andExpect(jsonPath("$.data.authResponse.accessToken").isString());
        login(s.email, s.password); // the password still works
    }

    @Test
    void google_suspendedAccountsCannotSignIn() throws Exception {
        Session s = registerUser(Role.DONOR);
        User u = userRepository.findById(s.userId).orElseThrow();
        u.setProviderId("sub-suspended");
        u.setStatus(com.suwasarana.api.user.UserStatus.SUSPENDED);
        userRepository.save(u);

        postJson("/api/auth/google/verify", Map.of("idToken", googleToken("sub-suspended", s.email)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(AuthCodes.ACCOUNT_SUSPENDED));
    }
}
