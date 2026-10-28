package com.suwasarana.api.admin;

import com.suwasarana.api.audit.AdminAuditLog;
import com.suwasarana.api.audit.AdminAuditLogRepository;
import com.suwasarana.api.audit.AuditAction;
import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.AuthException;
import com.suwasarana.api.security.VerificationGuard;
import com.suwasarana.api.support.IntegrationTestBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserStatus;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin workflow end to end: one verification API, mandatory reasons, audit rows, notifications, suspension, invitations. */
class AdminWorkflowIntegrationTest extends IntegrationTestBase {

    @Autowired private AdminAuditLogRepository auditRepository;
    @Autowired private VerificationGuard verificationGuard;

    private List<AdminAuditLog> auditFor(Long targetUserId, AuditAction action) {
        return auditRepository.findAll().stream()
                .filter(a -> targetUserId.equals(a.getTargetUserId()) && a.getAction().equals(action.name()))
                .toList();
    }

    // ---- queue ------------------------------------------------------------------------------------------------

    @Test
    void queue_listsInstitutionsOnly_withDetails_neverDonors() throws Exception {
        Session admin = createAdmin();
        Session hospital = registerUser(Role.HOSPITAL_REQUESTER);
        Session bank = registerUser(Role.BLOOD_BANK_REQUESTER);
        Session donor = registerUser(Role.DONOR);

        MvcResult result = getJson("/api/admin/verifications/pending?size=100", admin.accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andReturn();
        String body = result.getResponse().getContentAsString();

        assertTrue(body.contains(hospital.email) && body.contains(bank.email), "institutions are listed");
        assertFalse(body.contains(donor.email), "donors are not");
        assertTrue(body.contains("\"role\":\"HOSPITAL_REQUESTER\"") && body.contains("\"emailVerified\":false"));
        assertTrue(body.contains(hospital.phone), "phone shown to the reviewer");
        assertFalse(body.toLowerCase().contains("passwordhash"));
        assertFalse(body.contains("nic"), "no NIC data in the queue");

        getJson("/api/admin/verifications/pending?role=REQUESTER", admin.accessToken).andExpect(status().isOk());
        getJson("/api/admin/verifications/pending?role=DONOR", admin.accessToken).andExpect(status().isBadRequest());
    }

    @Test
    void queue_isForAdminsOnly() throws Exception {
        Session hospital = registerUser(Role.HOSPITAL_REQUESTER);
        Session donor = registerUser(Role.DONOR);

        getJson("/api/admin/verifications/pending", null).andExpect(status().isUnauthorized());
        getJson("/api/admin/verifications/pending", donor.accessToken).andExpect(status().isForbidden());
        getJson("/api/admin/verifications/pending", hospital.accessToken).andExpect(status().isForbidden());
        putJson("/api/admin/verifications/" + hospital.userId + "/approve", Map.of("reason", "self service"), hospital.accessToken)
                .andExpect(status().isForbidden());
        assertEquals(VerificationStatus.PENDING, userRepository.findById(hospital.userId).orElseThrow().getVerificationStatus());
    }

    // ---- approve / reject ---------------------------------------------------------------------------------------

    @Test
    void approve_needsAReason() throws Exception {
        Session admin = createAdmin();
        Session hospital = registerUser(Role.HOSPITAL_REQUESTER);
        String path = "/api/admin/verifications/" + hospital.userId + "/approve";

        putJson(path, Map.of(), admin.accessToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.reason").value(AuthCodes.REASON_REQUIRED));
        putJson(path, Map.of("reason", "  "), admin.accessToken).andExpect(status().isBadRequest());
        putJson(path, Map.of("reason", "ok"), admin.accessToken).andExpect(status().isBadRequest());
        putJson(path, Map.of("reason", "x".repeat(501)), admin.accessToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.reason").value(AuthCodes.REASON_TOO_LONG));
        putJson(path, null, admin.accessToken).andExpect(status().is4xxClientError());

        assertEquals(VerificationStatus.PENDING, userRepository.findById(hospital.userId).orElseThrow().getVerificationStatus());
    }

    @Test
    void approve_persistsTheDecision_writesAnAuditRow_andNotifiesTheUser() throws Exception {
        Session admin = createAdmin();
        Session hospital = registerUser(Role.HOSPITAL_REQUESTER);
        String verifyToken = tokenFromLastEmail(hospital.email);
        outbox.clear();

        putJson("/api/admin/verifications/" + hospital.userId + "/approve",
                Map.of("reason", "Registration number 123 matches the health ministry list"), admin.accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));

        User u = userRepository.findById(hospital.userId).orElseThrow();
        assertEquals(VerificationStatus.VERIFIED, u.getVerificationStatus());
        assertEquals(admin.userId, u.getVerificationReviewedBy());
        assertNotNull(u.getVerificationReviewedAt());
        assertEquals("Registration number 123 matches the health ministry list", u.getVerificationReason());

        List<AdminAuditLog> rows = auditFor(hospital.userId, AuditAction.VERIFICATION_APPROVED);
        assertEquals(1, rows.size());
        AdminAuditLog row = rows.get(0);
        assertEquals(admin.userId, row.getActorId());
        assertEquals("ADMIN", row.getActorRole());
        assertEquals("Registration number 123 matches the health ministry list", row.getReason());
        assertNotNull(row.getIpAddress());
        assertNotNull(row.getCreatedAt());

        var mail = outbox.lastEmailTo(hospital.email);
        assertNotNull(mail);
        assertTrue(mail.body().contains("Registration number 123"), "the reviewer's note reaches the user");
        assertNotNull(outbox.lastSmsTo(hospital.phone));
        assertTrue(outbox.lastSmsTo(hospital.phone).text().toLowerCase().contains("verified"));
        assertFalse(outbox.lastSmsTo(hospital.phone).text().contains("Registration number"), "SMS never carries the free-text reason");

        // the institution can now publish
        assertThrows(AuthException.class, () -> verificationGuard.requireVerified(hospital.userId), "email still unconfirmed");
        postJson("/api/auth/verify-email", Map.of("token", verifyToken)).andExpect(status().isOk());
        assertDoesNotThrow(() -> verificationGuard.requireVerified(hospital.userId));
    }

    @Test
    void reject_blocksPublishing_isAuditedAndCanBeReversed() throws Exception {
        Session admin = createAdmin();
        Session bank = registerUser(Role.BLOOD_BANK_REQUESTER);
        String reject = "/api/admin/verifications/" + bank.userId + "/reject";

        putJson(reject, Map.of("reason", "Licence document unreadable"), admin.accessToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"));
        putJson(reject, Map.of("reason", "Licence document unreadable"), admin.accessToken)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(AuthCodes.ADMIN_ALREADY_DECIDED));

        AuthException ex = assertThrows(AuthException.class, () -> verificationGuard.requireVerified(bank.userId));
        assertEquals(AuthCodes.VERIFICATION_REJECTED, ex.getCode());
        assertEquals(AuthCodes.VERIFICATION_REJECTED, assertThrows(AuthException.class, () -> verificationGuard.requireNotBlocked(bank.userId)).getCode());
        assertTrue(outbox.lastEmailTo(bank.email).body().contains("Licence document unreadable"));
        assertEquals(1, auditFor(bank.userId, AuditAction.VERIFICATION_REJECTED).size());

        // the queue only shows PENDING; the rejected list is available by status
        assertFalse(getJson("/api/admin/verifications/pending", admin.accessToken).andReturn().getResponse().getContentAsString().contains(bank.email));
        assertTrue(getJson("/api/admin/verifications?status=REJECTED", admin.accessToken).andReturn().getResponse().getContentAsString().contains(bank.email));

        putJson("/api/admin/verifications/" + bank.userId + "/approve", Map.of("reason", "Second review: documents provided"), admin.accessToken)
                .andExpect(status().isOk());
        assertEquals(1, auditFor(bank.userId, AuditAction.VERIFICATION_APPROVED).size());
    }

    @Test
    void donors_andAdmins_cannotBeVerifiedThroughTheQueue() throws Exception {
        Session admin = createAdmin();
        Session other = createAdmin();
        Session donor = registerUser(Role.DONOR);

        putJson("/api/admin/verifications/" + donor.userId + "/approve", Map.of("reason", "should not apply"), admin.accessToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(AuthCodes.ADMIN_NOT_VERIFIABLE));
        putJson("/api/admin/verifications/" + other.userId + "/reject", Map.of("reason", "attempt on an admin"), admin.accessToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(AuthCodes.ADMIN_TARGET_PROTECTED));
        putJson("/api/admin/verifications/" + admin.userId + "/reject", Map.of("reason", "attempt on myself"), admin.accessToken)
                .andExpect(status().isForbidden());
        putJson("/api/admin/verifications/9999999/approve", Map.of("reason", "no such user"), admin.accessToken)
                .andExpect(status().isNotFound());
        assertEquals(VerificationStatus.VERIFIED, userRepository.findById(other.userId).orElseThrow().getVerificationStatus());
    }

    // ---- suspension ---------------------------------------------------------------------------------------------

    @Test
    void suspend_blocksEverythingImmediately_andRestoreBringsTheUserBack() throws Exception {
        Session admin = createAdmin();
        Session donor = registerUser(Role.DONOR);
        getJson("/api/users/me", donor.accessToken).andExpect(status().isOk());
        String path = "/api/admin/users/" + donor.userId;

        postJson(path + "/suspend", Map.of(), admin.accessToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.reason").value(AuthCodes.REASON_REQUIRED));
        postJson(path + "/suspend", Map.of("reason", "Repeated abusive messages"), admin.accessToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        // the access token that was valid a moment ago, and the refresh cookie, are dead
        getJson("/api/users/me", donor.accessToken).andExpect(status().isUnauthorized());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, donor.refreshCookie)
                .andExpect(status().isUnauthorized());
        // login: a wrong password reveals nothing; the right one explains
        postJson("/api/auth/login", Map.of("username", donor.email, "password", "wrong-password-1")).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("username", donor.email, "password", donor.password))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(AuthCodes.ACCOUNT_SUSPENDED));
        assertEquals(AuthCodes.ACCOUNT_SUSPENDED, assertThrows(AuthException.class, () -> verificationGuard.requireNotBlocked(donor.userId)).getCode());
        postJson(path + "/suspend", Map.of("reason", "again"), admin.accessToken).andExpect(status().isConflict());
        assertEquals(1, auditFor(donor.userId, AuditAction.USER_SUSPENDED).size());
        assertTrue(outbox.lastEmailTo(donor.email).body().contains("Repeated abusive messages"));

        postJson(path + "/restore", Map.of("reason", "Appeal accepted"), admin.accessToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        login(donor.email, donor.password);
        assertEquals(1, auditFor(donor.userId, AuditAction.USER_RESTORED).size());
        postJson(path + "/restore", Map.of("reason", "again"), admin.accessToken).andExpect(status().isConflict());
    }

    @Test
    void suspend_cannotTargetAdmins() throws Exception {
        Session admin = createAdmin();
        Session other = createAdmin();

        postJson("/api/admin/users/" + other.userId + "/suspend", Map.of("reason", "coup attempt"), admin.accessToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(AuthCodes.ADMIN_TARGET_PROTECTED));
        assertEquals(UserStatus.ACTIVE, userRepository.findById(other.userId).orElseThrow().getStatus());
    }

    // ---- role change --------------------------------------------------------------------------------------------

    @Test
    void roleChange_restartsVerification_isAudited_andNeverGrantsAdmin() throws Exception {
        Session admin = createAdmin();
        Session donor = registerUser(Role.DONOR);
        String path = "/api/admin/users/" + donor.userId + "/role";

        putJson(path, Map.of("role", "ADMIN", "reason", "make admin"), admin.accessToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(AuthCodes.ADMIN_ROLE_INVALID));
        putJson(path, Map.of("role", "HOSPITAL_REQUESTER"), admin.accessToken).andExpect(status().isBadRequest());
        putJson(path, Map.of("role", "HOSPITAL_REQUESTER", "reason", "Works at the district hospital"), admin.accessToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("HOSPITAL_REQUESTER"))
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"));

        assertEquals(1, auditFor(donor.userId, AuditAction.ROLE_CHANGED).size());
        assertTrue(auditFor(donor.userId, AuditAction.ROLE_CHANGED).get(0).getDetails().contains("DONOR -> HOSPITAL_REQUESTER"));
        getJson("/api/users/me", donor.accessToken).andExpect(status().isUnauthorized()); // forced to sign in again with the new role
    }

    // ---- search, audit read, export -------------------------------------------------------------------------------

    @Test
    void search_andAuditLog_areReadableByAdminsOnly() throws Exception {
        Session admin = createAdmin();
        Session donor = registerUser(Role.DONOR);
        postJson("/api/admin/users/" + donor.userId + "/suspend", Map.of("reason", "Test suspension"), admin.accessToken).andExpect(status().isOk());

        String found = getJson("/api/admin/users?q=" + donor.email.substring(0, 10) + "&status=SUSPENDED", admin.accessToken)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(found.contains(donor.email));
        assertFalse(found.contains("password"));

        String log = getJson("/api/admin/audit?action=USER_SUSPENDED&targetUserId=" + donor.userId, admin.accessToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].action").value("USER_SUSPENDED"))
                .andExpect(jsonPath("$.data.items[0].reason").value("Test suspension")).andReturn().getResponse().getContentAsString();
        assertTrue(log.contains("\"actorId\":" + admin.userId));

        Session bystander = registerUser(Role.DONOR);
        getJson("/api/admin/audit", bystander.accessToken).andExpect(status().isForbidden());
        getJson("/api/admin/users", bystander.accessToken).andExpect(status().isForbidden());
        getJson("/api/admin/audit", null).andExpect(status().isUnauthorized());
    }

    @Test
    void adminExport_needsAReason_andIsAudited() throws Exception {
        Session admin = createAdmin();
        Session donor = registerUser(Role.DONOR);
        String path = "/api/admin/users/" + donor.userId + "/export";

        getJson(path, admin.accessToken).andExpect(status().is4xxClientError());
        getJson(path + "?reason=ab", admin.accessToken).andExpect(status().isBadRequest());
        MvcResult ok = getJson(path + "?reason=Data subject request 2026-044", admin.accessToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.email").value(donor.email)).andReturn();
        assertTrue(ok.getResponse().getHeader("Content-Disposition").contains("attachment"));
        assertEquals(1, auditFor(donor.userId, AuditAction.DATA_EXPORT).size());
        getJson(path + "?reason=Data subject request", donor.accessToken).andExpect(status().isForbidden());
    }

    // ---- invitations -----------------------------------------------------------------------------------------------

    @Test
    void invitation_createsAnAdmin_withoutSql_singleUse() throws Exception {
        Session admin = createAdmin();
        Session donor = registerUser(Role.DONOR);
        String invitee = uniqueEmail("newadmin");

        postJson("/api/admin/invitations", Map.of("email", invitee), null).andExpect(status().isUnauthorized());
        postJson("/api/admin/invitations", Map.of("email", invitee), donor.accessToken).andExpect(status().isForbidden());
        postJson("/api/admin/invitations", Map.of("email", "not-an-email"), admin.accessToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.email").value(AuthCodes.EMAIL_INVALID));
        postJson("/api/admin/invitations", Map.of("email", donor.email), admin.accessToken)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(AuthCodes.INVITE_EXISTS));

        MvcResult invited = postJson("/api/admin/invitations", Map.of("email", invitee, "firstName", "Kamal", "preferredLanguage", "ta"), admin.accessToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(invitee)).andReturn();
        assertFalse(invited.getResponse().getContentAsString().contains("token"), "the raw token is never returned");
        String token = tokenFromLastEmail(invitee);
        assertTrue(outbox.lastEmailTo(invitee).body().contains("/accept-invite?token="));
        assertTrue(outbox.lastEmailTo(invitee).subject().chars().anyMatch(c -> c >= 0x0B80 && c <= 0x0BFF), "invitation in Tamil");
        assertEquals(1, auditRepository.findAll().stream().filter(a -> a.getAction().equals("ADMIN_INVITED") && admin.userId.equals(a.getActorId())).count());

        String phone = uniquePhone();
        Map<String, Object> accept = new java.util.HashMap<>(Map.of("token", token, "firstName", "Kamal", "lastName", "Fernando",
                "phoneNumber", phone, "password", STRONG_PASSWORD, "acceptTerms", true));

        postJson("/api/auth/accept-invite", accept).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.phoneNumber").value(AuthCodes.PHONE_NOT_VERIFIED));
        verifyPhone(phone);
        accept.put("password", "password1234");
        postJson("/api/auth/accept-invite", accept).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.password").value(AuthCodes.PASSWORD_COMMON));
        accept.put("password", STRONG_PASSWORD);
        accept.put("token", "forged-token");
        postJson("/api/auth/accept-invite", accept).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(AuthCodes.INVITE_INVALID));

        // none of the failed attempts burned the real token
        accept.put("token", token);
        MvcResult done = postJson("/api/auth/accept-invite", accept).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN")).andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.emailVerified").value(true)).andReturn();
        String newAdminToken = jsonOf(done, "$.data.accessToken");
        getJson("/api/admin/verifications/pending", newAdminToken).andExpect(status().isOk());
        login(invitee, STRONG_PASSWORD);

        // single use
        postJson("/api/auth/accept-invite", accept).andExpect(status().isBadRequest());
        String newAdminId = jsonOf(done, "$.data.userId");
        assertEquals(1, auditRepository.findAll().stream().filter(a -> a.getAction().equals("ADMIN_INVITE_ACCEPTED")
                && newAdminId.equals(String.valueOf(a.getActorId()))).count());
    }

    @Test
    void invitation_expiredLinksAreRefused() throws Exception {
        Session admin = createAdmin();
        String invitee = uniqueEmail("late");
        postJson("/api/admin/invitations", Map.of("email", invitee), admin.accessToken).andExpect(status().isOk());
        String token = tokenFromLastEmail(invitee);
        String phone = uniquePhone();
        verifyPhone(phone);

        authTokenRepositoryExpireAll();

        postJson("/api/auth/accept-invite", Map.of("token", token, "firstName", "L", "lastName", "Late", "phoneNumber", phone,
                "password", STRONG_PASSWORD, "acceptTerms", true))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(AuthCodes.INVITE_INVALID));
    }

    @Autowired private com.suwasarana.api.auth.token.AuthTokenRepository authTokenRepository;

    private void authTokenRepositoryExpireAll() {
        authTokenRepository.findAll().forEach(t -> {
            t.setExpiresAt(Instant.now().minusSeconds(5));
            authTokenRepository.save(t);
        });
    }
}
