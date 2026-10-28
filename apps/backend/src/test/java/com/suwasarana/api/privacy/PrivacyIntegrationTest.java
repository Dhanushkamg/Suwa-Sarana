package com.suwasarana.api.privacy;

import com.jayway.jsonpath.JsonPath;
import com.suwasarana.api.audit.AdminAuditLogRepository;
import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.support.IntegrationTestBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PDPA: consent, own profile, phone/password change, export of everything held, and erasure by anonymisation. */
class PrivacyIntegrationTest extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AdminAuditLogRepository auditRepository;

    // ---- data a donor accumulates (through the schema, independent of other modules' Java APIs) ------------------

    private long insert(String table, Map<String, Object> columns) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingGeneratedKeyColumns("id").executeAndReturnKey(columns).longValue();
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    private long donorProfile(long userId) {
        Map<String, Object> c = new HashMap<>();
        c.put("user_id", userId);
        c.put("blood_type", "O+");
        c.put("date_of_birth", java.sql.Date.valueOf("1992-04-12"));
        c.put("weight_kg", new java.math.BigDecimal("68.50"));
        c.put("district", "Colombo");
        c.put("latitude", 6.9271);
        c.put("longitude", 79.8612);
        c.put("is_available", true);
        c.put("reliability_score", new java.math.BigDecimal("77.00"));
        c.put("total_donations", 3);
        c.put("lives_helped_estimate", 9);
        return insert("donor_profiles", c);
    }

    private long bloodRequest(long requesterId, String status) {
        Map<String, Object> c = new HashMap<>();
        c.put("requester_id", requesterId);
        c.put("patient_blood_type", "A+");
        c.put("units_needed", 2);
        c.put("urgency", "URGENT");
        c.put("hospital_name", "General Hospital");
        c.put("district", "Colombo");
        c.put("latitude", 6.92);
        c.put("longitude", 79.86);
        c.put("status", status);
        c.put("current_radius_km", 5);
        c.put("created_at", ts(LocalDateTime.now()));
        c.put("expires_at", ts(LocalDateTime.now().plusHours(12)));
        c.put("last_escalated_at", ts(LocalDateTime.now()));
        return insert("blood_requests", c);
    }

    private long slot(long hospitalUserId, int capacity, int booked) {
        Map<String, Object> c = new HashMap<>();
        c.put("hospital_id", hospitalUserId);
        c.put("start_time", ts(LocalDateTime.now().plusDays(2)));
        c.put("end_time", ts(LocalDateTime.now().plusDays(2).plusHours(1)));
        c.put("capacity", capacity);
        c.put("booked_count", booked);
        c.put("status", booked >= capacity ? "FULL" : "OPEN");
        c.put("created_at", ts(LocalDateTime.now()));
        c.put("updated_at", ts(LocalDateTime.now()));
        return insert("donation_slots", c);
    }

    private static class DonorWorld {
        Session donor;
        long profileId;
        long openRequestId;
        long matchId;
        long slotId;
        long bookingId;
        long notificationId;
        long contactId;
    }

    private DonorWorld donorWithHistory() throws Exception {
        DonorWorld w = new DonorWorld();
        w.donor = registerUser(Role.DONOR);
        Session hospital = registerUser(Role.HOSPITAL_REQUESTER);
        w.profileId = donorProfile(w.donor.userId);

        w.openRequestId = bloodRequest(w.donor.userId, "OPEN");          // the donor also asked for blood once
        long hospitalRequest = bloodRequest(hospital.userId, "OPEN");    // and was matched to someone else's request

        Map<String, Object> match = new HashMap<>();
        match.put("request_id", hospitalRequest);
        match.put("donor_id", w.profileId);
        match.put("distance_km", new java.math.BigDecimal("3.20"));
        match.put("notified_at", ts(LocalDateTime.now()));
        match.put("response", "PENDING");
        w.matchId = insert("request_matches", match);

        w.slotId = slot(hospital.userId, 1, 1);
        Map<String, Object> booking = new HashMap<>();
        booking.put("slot_id", w.slotId);
        booking.put("donor_id", w.profileId);
        booking.put("status", "BOOKED");
        booking.put("booked_at", Timestamp.from(Instant.now()));
        w.bookingId = insert("slot_bookings", booking);

        Map<String, Object> log = new HashMap<>();
        log.put("donor_id", w.profileId);
        log.put("request_id", hospitalRequest);
        log.put("channel", "SMS");
        log.put("status", "SENT");
        log.put("recipient", w.donor.phone);
        log.put("message_body", "Urgent A+ blood needed at General Hospital");
        log.put("sent_at", ts(LocalDateTime.now()));
        w.notificationId = insert("notification_logs", log);

        Map<String, Object> contact = new HashMap<>();
        contact.put("name", "Test User");
        contact.put("email", w.donor.email);
        contact.put("subject", "Question");
        contact.put("message", "Can I donate after a tattoo?");
        contact.put("status", "NEW");
        contact.put("created_at", Timestamp.from(Instant.now()));
        w.contactId = insert("contact_messages", contact);
        return w;
    }

    // ---- /users/me ------------------------------------------------------------------------------------------------

    @Test
    void me_returnsTheOwnProfile_andOnlyToTheOwner() throws Exception {
        Session s = registerUser(Role.REQUESTER);

        getJson("/api/users/me", s.accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(s.email))
                .andExpect(jsonPath("$.data.phoneNumber").value(s.phone))
                .andExpect(jsonPath("$.data.role").value("REQUESTER"))
                .andExpect(jsonPath("$.data.hasPassword").value(true))
                .andExpect(jsonPath("$.data.googleLinked").value(false))
                .andExpect(jsonPath("$.data.preferredLanguage").value("en"))
                .andExpect(jsonPath("$.data.consentVersion").value("2026-01"))
                .andExpect(jsonPath("$.data.consentRequired").value(false))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        getJson("/api/users/me", null).andExpect(status().isUnauthorized());
    }

    @Test
    void patch_changesNameAndLanguage_validatesThem_andIgnoresUnknownFields() throws Exception {
        Session s = registerUser(Role.DONOR);

        patchJson("/api/users/me", Map.of("firstName", "Kasun", "lastName", "Jayasuriya", "preferredLanguage", "ta"), s.accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Kasun"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("ta"));
        patchJson("/api/users/me", Map.of("preferredLanguage", "fr"), s.accessToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.preferredLanguage").value(AuthCodes.LANGUAGE_INVALID));
        patchJson("/api/users/me", Map.of("firstName", "  "), s.accessToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.firstName").value(AuthCodes.FIRST_NAME_REQUIRED));
        // role / verification cannot be set from the client
        patchJson("/api/users/me", Map.of("role", "ADMIN", "verificationStatus", "VERIFIED", "email", "x@y.lk"), s.accessToken)
                .andExpect(status().isOk());
        User u = userRepository.findById(s.userId).orElseThrow();
        assertEquals(Role.DONOR, u.getRole());
        assertEquals(s.email, u.getEmail());
        assertEquals("PENDING", u.getVerificationStatus().name());
        assertEquals("Kasun", u.getFirstName());
    }

    @Test
    void phoneChange_needsAnOtpOnTheNewNumber_andCannotStealAnotherAccountsNumber() throws Exception {
        Session s = registerUser(Role.DONOR);
        Session other = registerUser(Role.DONOR);
        String newPhone = uniquePhone();

        patchJson("/api/users/me", Map.of("phoneNumber", newPhone), s.accessToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.phoneNumber").value(AuthCodes.PHONE_NOT_VERIFIED));
        patchJson("/api/users/me", Map.of("phoneNumber", "123"), s.accessToken).andExpect(status().isBadRequest());

        verifyPhone(newPhone);
        patchJson("/api/users/me", Map.of("phoneNumber", "0" + newPhone.substring(3)), s.accessToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phoneNumber").value(newPhone));
        login(newPhone, s.password);
        postJson("/api/auth/login", Map.of("username", s.phone, "password", s.password)).andExpect(status().isUnauthorized());

        verifyPhone(other.phone);
        patchJson("/api/users/me", Map.of("phoneNumber", other.phone), s.accessToken)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(AuthCodes.PHONE_IN_USE));
    }

    // ---- change password ----------------------------------------------------------------------------------------------

    @Test
    void changePassword_checksCurrent_appliesPolicy_andSignsOutEveryOtherSession() throws Exception {
        Session s = registerUser(Role.DONOR);
        Session otherDevice = login(s.email, s.password);

        postJson("/api/users/me/change-password", Map.of("currentPassword", "wrong-one-12345", "newPassword", "Brand-New-Passphrase-77"), s.accessToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.currentPassword").value(AuthCodes.PASSWORD_WRONG));
        postJson("/api/users/me/change-password", Map.of("currentPassword", s.password, "newPassword", "password1234"), s.accessToken)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.newPassword").value(AuthCodes.PASSWORD_COMMON));

        MvcResult ok = postJson("/api/users/me/change-password", Map.of("currentPassword", s.password, "newPassword", "Brand-New-Passphrase-77"), s.accessToken)
                .andExpect(status().isOk()).andReturn();
        String freshAccess = jsonOf(ok, "$.data.accessToken");
        assertNotNull(ok.getResponse().getCookie("refresh_token"));

        getJson("/api/users/me", freshAccess).andExpect(status().isOk());               // the caller keeps a working session
        getJson("/api/users/me", s.accessToken).andExpect(status().isUnauthorized());   // the old access token died
        getJson("/api/users/me", otherDevice.accessToken).andExpect(status().isUnauthorized());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, otherDevice.refreshCookie)
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("username", s.email, "password", s.password)).andExpect(status().isUnauthorized());
        login(s.email, "Brand-New-Passphrase-77");
    }

    // ---- consent ------------------------------------------------------------------------------------------------------

    @Test
    void consent_isRecordedAtSignUp_andLegacyAccountsCanAcceptTheCurrentTerms() throws Exception {
        Session s = registerUser(Role.DONOR);
        User u = userRepository.findById(s.userId).orElseThrow();
        assertEquals("2026-01", u.getConsentVersion());
        assertNotNull(u.getConsentedAt());

        // an account created before consent was recorded
        u.setConsentVersion(null);
        u.setConsentedAt(null);
        userRepository.save(u);
        getJson("/api/users/me", s.accessToken).andExpect(jsonPath("$.data.consentRequired").value(true));

        postJson("/api/users/me/consent", null, s.accessToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.consentRequired").value(false))
                .andExpect(jsonPath("$.data.consentVersion").value("2026-01"));
        assertNotNull(userRepository.findById(s.userId).orElseThrow().getConsentedAt());
    }

    // ---- export ---------------------------------------------------------------------------------------------------------

    @Test
    void export_containsEverythingHeldAboutTheUser_andNoSecrets() throws Exception {
        DonorWorld w = donorWithHistory();

        MvcResult result = getJson("/api/users/me/export", w.donor.accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(w.donor.email))
                .andExpect(jsonPath("$.profile.phoneNumber").value(w.donor.phone))
                .andExpect(jsonPath("$.profile.role").value("DONOR"))
                .andExpect(jsonPath("$.consent.version").value("2026-01"))
                .andExpect(jsonPath("$.donorProfile.blood_type").value("O+"))
                .andExpect(jsonPath("$.donorProfile.latitude").value(6.9271))
                .andReturn();
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("attachment"));
        String json = result.getResponse().getContentAsString();

        assertEquals(1, ((List<?>) JsonPath.read(json, "$.bloodRequests")).size(), "requests the user made");
        assertEquals(1, ((List<?>) JsonPath.read(json, "$.matches")).size());
        assertEquals(1, ((List<?>) JsonPath.read(json, "$.slotBookings")).size());
        assertEquals(1, ((List<?>) JsonPath.read(json, "$.notificationLogs")).size());
        assertEquals(1, ((List<?>) JsonPath.read(json, "$.contactMessages")).size());
        assertTrue(json.contains("Urgent A+ blood needed at General Hospital"), "notification text is part of what is held");
        assertTrue(json.contains("Can I donate after a tattoo?"));
        assertEquals(1, ((Number) JsonPath.read(json, "$.activeSessions")).intValue());

        assertFalse(json.contains("password_hash") || json.contains("passwordHash"), "no password hash");
        assertFalse(json.contains("token_hash") || json.contains("tokenHash"), "no token hashes");
        assertFalse(json.contains(w.donor.password));

        // the export itself is recorded
        assertEquals(1, auditRepository.findAll().stream().filter(a -> a.getAction().equals("DATA_EXPORT")
                && w.donor.userId.equals(a.getActorId())).count());
        getJson("/api/users/me/export", null).andExpect(status().isUnauthorized());
    }

    @Test
    void export_ofAnAccountWithNothingElse_isStillWellFormed() throws Exception {
        Session s = registerUser(Role.REQUESTER);

        getJson("/api/users/me/export", s.accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.donorProfile").doesNotExist())
                .andExpect(jsonPath("$.bloodRequests").isEmpty())
                .andExpect(jsonPath("$.matches").isEmpty());
    }

    // ---- erasure --------------------------------------------------------------------------------------------------------

    @Test
    void delete_needsConfirmation_andACorrectPassword() throws Exception {
        Session s = registerUser(Role.DONOR);

        deleteJson("/api/users/me", null, s.accessToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthCodes.DELETE_CONFIRMATION_REQUIRED));
        deleteJson("/api/users/me", Map.of("password", "not-my-password"), s.accessToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthCodes.DELETE_CONFIRMATION_INVALID));
        deleteJson("/api/users/me", Map.of("otpCode", "123456"), s.accessToken).andExpect(status().isForbidden());

        assertEquals(UserStatus.ACTIVE, userRepository.findById(s.userId).orElseThrow().getStatus());
        assertTrue(userRepository.findById(s.userId).orElseThrow().getFailedLoginCount() >= 1, "wrong confirmations count towards the lockout");
    }

    @Test
    void delete_anonymises_scrubsDonorData_keepsHistoryWithoutPersonalData_andCausesNoForeignKeyFailure() throws Exception {
        DonorWorld w = donorWithHistory();
        long matchesBefore = jdbc.queryForObject("select count(*) from request_matches", Long.class);
        long requestsBefore = jdbc.queryForObject("select count(*) from blood_requests", Long.class);
        String oldEmail = w.donor.email;
        String oldPhone = w.donor.phone;
        outbox.clear();

        deleteJson("/api/users/me", Map.of("password", w.donor.password), w.donor.accessToken).andExpect(status().isOk());

        // the account row: nothing personal left
        Map<String, Object> row = jdbc.queryForMap("select * from users where id = ?", w.donor.userId);
        assertEquals("deleted-" + w.donor.userId + "@deleted.invalid", row.get("email"));
        assertEquals("deleted-" + w.donor.userId, row.get("phone_number"));
        assertEquals("Deleted", row.get("first_name"));
        assertEquals("User", row.get("last_name"));
        assertNull(row.get("password_hash"));
        assertNull(row.get("nic_hash"));
        assertNull(row.get("provider_id"));
        assertNull(row.get("email_verified_at"));
        assertEquals("DELETED", row.get("status"));
        assertNotNull(row.get("deleted_at"));

        // donor details are scrubbed, the profile row stays so history keeps its foreign keys
        Map<String, Object> donor = jdbc.queryForMap("select * from donor_profiles where id = ?", w.profileId);
        assertEquals(0.0, ((Number) donor.get("latitude")).doubleValue());
        assertEquals(0.0, ((Number) donor.get("longitude")).doubleValue());
        assertEquals("1970-01-01", donor.get("date_of_birth").toString());
        assertNull(donor.get("weight_kg"));
        assertEquals(Boolean.FALSE, donor.get("is_available"));

        // history is aggregate-only now
        assertEquals(matchesBefore, jdbc.queryForObject("select count(*) from request_matches", Long.class));
        assertEquals(requestsBefore, jdbc.queryForObject("select count(*) from blood_requests", Long.class));
        assertEquals("CANCELLED", jdbc.queryForObject("select status from blood_requests where id = ?", String.class, w.openRequestId));
        assertEquals("CANCELLED", jdbc.queryForObject("select status from slot_bookings where id = ?", String.class, w.bookingId));
        assertEquals(0, jdbc.queryForObject("select booked_count from donation_slots where id = ?", Integer.class, w.slotId), "the seat is released");
        assertEquals("OPEN", jdbc.queryForObject("select status from donation_slots where id = ?", String.class, w.slotId));
        assertNull(jdbc.queryForObject("select message_body from notification_logs where id = ?", String.class, w.notificationId));
        assertNull(jdbc.queryForObject("select recipient from notification_logs where id = ?", String.class, w.notificationId));
        assertEquals("NO_RESPONSE", jdbc.queryForObject("select response from request_matches where id = ?", String.class, w.matchId));
        assertEquals(0, jdbc.queryForObject("select count(*) from contact_messages where id = ?", Integer.class, w.contactId));
        assertEquals(0, jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?", Integer.class, w.donor.userId));
        assertEquals(0, jdbc.queryForObject("select count(*) from auth_tokens where user_id = ?", Integer.class, w.donor.userId));

        // the person can no longer get in, by any route
        getJson("/api/users/me", w.donor.accessToken).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("username", oldEmail, "password", w.donor.password)).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("username", oldPhone, "password", w.donor.password)).andExpect(status().isUnauthorized());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh"), null, null, w.donor.refreshCookie)
                .andExpect(status().isUnauthorized());

        // the audit trail records the erasure without personal data; the person is told by email
        var entries = auditRepository.findAll().stream().filter(a -> a.getAction().equals("ACCOUNT_DELETED") && w.donor.userId.equals(a.getTargetUserId())).toList();
        assertEquals(1, entries.size());
        assertFalse((entries.get(0).getDetails() + entries.get(0).getReason()).contains(oldEmail));
        assertNotNull(outbox.lastEmailTo(oldEmail));

        // and both identifiers are free again
        verifyPhone(oldPhone);
        postJson("/api/auth/register", Map.of("email", oldEmail, "phoneNumber", oldPhone, "password", STRONG_PASSWORD, "firstName", "New",
                "lastName", "Person", "role", "DONOR", "acceptTerms", true)).andExpect(status().isOk());
    }

    @Test
    void delete_withAnSmsCode_worksForAccountsWithoutAPassword() throws Exception {
        Session s = registerUser(Role.DONOR);
        outbox.clear();

        postJson("/api/users/me/send-otp", null, s.accessToken).andExpect(status().isOk());
        String code = lastOtpCode(s.phone);
        deleteJson("/api/users/me", Map.of("otpCode", code), s.accessToken).andExpect(status().isOk());

        assertEquals(UserStatus.DELETED, userRepository.findById(s.userId).orElseThrow().getStatus());
    }

    @Test
    void delete_isNotSelfServiceForAdministrators() throws Exception {
        Session admin = createAdmin();

        deleteJson("/api/users/me", Map.of("password", STRONG_PASSWORD), admin.accessToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(AuthCodes.DELETE_ADMIN_FORBIDDEN));
        assertEquals(UserStatus.ACTIVE, userRepository.findById(admin.userId).orElseThrow().getStatus());
    }

    @Test
    void delete_ofAnInstitution_cancelsItsFutureSlotsAndCamps() throws Exception {
        Session hospital = registerUser(Role.HOSPITAL_REQUESTER);
        long slotId = slot(hospital.userId, 5, 0);
        Map<String, Object> camp = new HashMap<>();
        camp.put("name", "City camp");
        camp.put("district", "Colombo");
        camp.put("location", "Town Hall");
        camp.put("scheduled_date", java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(5)));
        camp.put("start_time", java.sql.Time.valueOf("09:00:00"));
        camp.put("end_time", java.sql.Time.valueOf("15:00:00"));
        camp.put("organizer_id", hospital.userId);
        camp.put("status", "SCHEDULED");
        camp.put("created_at", Timestamp.from(Instant.now()));
        camp.put("updated_at", Timestamp.from(Instant.now()));
        long campId = insert("donation_camps", camp);

        deleteJson("/api/users/me", Map.of("password", hospital.password), hospital.accessToken).andExpect(status().isOk());

        assertEquals("CANCELLED", jdbc.queryForObject("select status from donation_slots where id = ?", String.class, slotId));
        assertEquals("CANCELLED", jdbc.queryForObject("select status from donation_camps where id = ?", String.class, campId));
    }
}
