package com.suwasarana.api.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.matching.EscalationProcessor;
import com.suwasarana.api.matching.MatchStatus;
import com.suwasarana.api.matching.MatchTimeoutProcessor;
import com.suwasarana.api.matching.MatchingEngine;
import com.suwasarana.api.matching.RequestMatch;
import com.suwasarana.api.testsupport.LifecycleIntegrationBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestLifecycleIntegrationTest extends LifecycleIntegrationBase {

    @Autowired private EscalationProcessor escalationProcessor;
    @Autowired private MatchTimeoutProcessor matchTimeoutProcessor;
    @Autowired private MatchingEngine matchingEngine;
    @Autowired private PlatformTransactionManager txManager;

    private User requester;
    private User other;
    private User admin;
    private User donorUser1;
    private User donorUser2;
    private DonorProfile donor1;
    private DonorProfile donor2;

    @BeforeEach
    void fixtures() {
        requester = user("requester", Role.REQUESTER, VerificationStatus.VERIFIED);
        other = user("other", Role.REQUESTER, VerificationStatus.VERIFIED);
        admin = user("admin", Role.ADMIN, VerificationStatus.VERIFIED);
        donorUser1 = user("donorone", Role.DONOR, VerificationStatus.VERIFIED);
        donorUser2 = user("donortwo", Role.DONOR, VerificationStatus.VERIFIED);
        donor1 = donor(donorUser1, "A+", "Colombo");
        donor2 = donor(donorUser2, "O-", "Colombo");
    }

    private String createBody(String bloodType, int units, String urgency, String hospital, String district, double lat, double lon) {
        return "{\"patientBloodType\":\"" + bloodType + "\",\"unitsNeeded\":" + units + ",\"urgency\":\"" + urgency
                + "\",\"hospitalName\":\"" + hospital + "\",\"district\":\"" + district + "\",\"latitude\":" + lat + ",\"longitude\":" + lon + "}";
    }

    private MvcResult create(User as, String body) throws Exception {
        return mvc.perform(post("/api/requests").with(as(as)).with(fromNewClient()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private long createOk(User as, int units) throws Exception {
        MvcResult r = create(as, createBody("A+", units, "URGENT", "Base Hospital Colombo", "Colombo", 6.93, 79.86));
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return data(r).get("id").asLong();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // ------------------------------------------------------------------ creation, matching, alerts

    @Test
    @DisplayName("Creating a request normalises the district, matches donors with distance and alerts them after commit")
    void create_matchesAndAlertsAfterCommit() throws Exception {
        stubCandidates(donor1, donor2);

        MvcResult res = create(requester, createBody("A+", 1, "URGENT", "Base Hospital Colombo", "colombo district", 6.93, 79.86));

        assertEquals(200, res.getResponse().getStatus(), res.getResponse().getContentAsString());
        JsonNode d = data(res);
        assertEquals("Colombo", d.get("district").asText());
        assertEquals("ESCALATING", d.get("status").asText());
        long id = d.get("id").asLong();

        List<RequestMatch> matches = matchRepository.findByRequestIdOrderByDistanceKmAscIdAsc(id);
        assertEquals(2, matches.size());
        assertEquals(0, new java.math.BigDecimal("1.00").compareTo(matches.get(0).getDistanceKm()));
        assertEquals(0, new java.math.BigDecimal("2.00").compareTo(matches.get(1).getDistanceKm()));
        assertEquals(MatchStatus.PENDING, matches.get(0).getStatus());

        // inbox rows were written for both donors; nobody is connected over SSE so the SMS fallback logged one each
        assertEquals(1, count("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'NEW_MATCH'", donorUser1.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'NEW_MATCH'", donorUser2.getId()));
        assertEquals(2, count("SELECT COUNT(*) FROM notification_logs WHERE request_id = ? AND channel = 'SMS'", id));
    }

    @Test
    @DisplayName("If the transaction that created the matches rolls back, no donor is alerted")
    void rolledBackMatching_alertsNobody() {
        BloodRequest r = request(requester, RequestStatus.ESCALATING, "A+", 1, "Colombo");
        stubCandidates(donor1);

        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
            matchingEngine.runMatchingForRequest(r.getId());
            throw new IllegalStateException("something later in the request failed");
        }));

        assertEquals(0, count("SELECT COUNT(*) FROM request_matches"));
        assertEquals(0, count("SELECT COUNT(*) FROM user_notifications"));
        assertEquals(0, count("SELECT COUNT(*) FROM notification_logs"));
    }

    @Test
    void create_rejectsUnknownDistrictAndLocationsOutsideSriLanka() throws Exception {
        MvcResult unknown = create(requester, createBody("A+", 1, "URGENT", "Hospital", "Atlantis", 6.93, 79.86));
        assertEquals(400, unknown.getResponse().getStatus());
        assertEquals("UNKNOWN_DISTRICT", json(unknown).get("code").asText());

        MvcResult nullIsland = create(requester, createBody("A+", 1, "URGENT", "Hospital", "Colombo", 0.0, 0.0));
        assertEquals(400, nullIsland.getResponse().getStatus());

        MvcResult chennai = create(requester, createBody("A+", 1, "URGENT", "Hospital", "Colombo", 13.08, 80.27));
        assertEquals(400, chennai.getResponse().getStatus());
    }

    @Test
    void create_suppressesDuplicatesAndCapsActiveRequests() throws Exception {
        assertEquals(200, create(requester, createBody("A+", 1, "URGENT", "Base Hospital Colombo", "Colombo", 6.93, 79.86)).getResponse().getStatus());

        MvcResult dup = create(requester, createBody("A+", 2, "ROUTINE", "base hospital colombo", "Colombo", 6.93, 79.86));
        assertEquals(409, dup.getResponse().getStatus());
        assertEquals("REQUEST_DUPLICATE", json(dup).get("code").asText());

        assertEquals(200, create(requester, createBody("B+", 1, "URGENT", "Hospital Two", "Kandy", 7.29, 80.63)).getResponse().getStatus());
        assertEquals(200, create(requester, createBody("O+", 1, "URGENT", "Hospital Three", "Galle", 6.05, 80.22)).getResponse().getStatus());
        MvcResult capped = create(requester, createBody("AB+", 1, "URGENT", "Hospital Four", "Matara", 5.95, 80.55));
        assertEquals(429, capped.getResponse().getStatus());
        assertEquals("REQUEST_LIMIT_REACHED", json(capped).get("code").asText());

        // the cap is per account
        assertEquals(200, create(other, createBody("AB+", 1, "URGENT", "Hospital Four", "Matara", 5.95, 80.55)).getResponse().getStatus());
    }

    @Test
    void create_blockedForRejectedAccounts_andCriticalNeedsVerification() throws Exception {
        User rejected = user("rejected", Role.REQUESTER, VerificationStatus.REJECTED);
        MvcResult r = create(rejected, createBody("A+", 1, "ROUTINE", "Hospital", "Colombo", 6.93, 79.86));
        assertEquals(403, r.getResponse().getStatus());
        assertEquals("REQUESTER_NOT_ALLOWED", json(r).get("code").asText());

        User pending = user("pending", Role.REQUESTER, VerificationStatus.PENDING);
        assertEquals(403, create(pending, createBody("A+", 1, "CRITICAL", "Hospital", "Colombo", 6.93, 79.86)).getResponse().getStatus());
        assertEquals(200, create(pending, createBody("A+", 1, "URGENT", "Hospital", "Colombo", 6.93, 79.86)).getResponse().getStatus());
    }

    // ------------------------------------------------------------------ the full lifecycle

    @Test
    @DisplayName("Open -> accepted (MATCHED) -> donation confirmed (FULFILLED), with history, deferral and totals")
    void fullLifecycle_toFulfilled() throws Exception {
        stubCandidates(donor1, donor2);
        long id = createOk(requester, 1);
        List<RequestMatch> matches = matchRepository.findByRequestIdOrderByDistanceKmAscIdAsc(id);
        RequestMatch m1 = matches.get(0);
        RequestMatch m2 = matches.get(1);

        // donor 1 accepts: the single needed unit is covered, so the request is MATCHED and the requester is told
        mvc.perform(post("/api/matches/" + m1.getId() + "/respond").param("response", "ACCEPTED").with(as(donorUser1)))
                .andExpect(status().isOk());
        BloodRequest afterAccept = requestRepository.findById(id).orElseThrow();
        assertEquals(RequestStatus.MATCHED, afterAccept.getStatus());
        assertNotNull(afterAccept.getMatchedAt());
        assertEquals(1, count("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'MATCH_ACCEPTED'", requester.getId()));

        // privacy: the requester sees the accepted donor's phone, but not the donor who has not answered
        MvcResult listed = mvc.perform(get("/api/requests/" + id + "/matches").with(as(requester))).andExpect(status().isOk()).andReturn();
        JsonNode ms = data(listed).get("matches");
        assertEquals(2, ms.size());
        JsonNode accepted = ms.get(0);
        JsonNode pending = ms.get(1);
        assertEquals("ACCEPTED", accepted.get("status").asText());
        assertEquals(donorUser1.getFirstName(), accepted.get("donorFirstName").asText());
        assertEquals(donorUser1.getPhoneNumber(), accepted.get("contact").get("phoneNumber").asText());
        assertEquals("PENDING", pending.get("status").asText());
        assertFalse(pending.has("contact"), "a donor who has not accepted must stay anonymous");
        assertFalse(listed.getResponse().getContentAsString().contains(donorUser2.getPhoneNumber()));
        assertFalse(listed.getResponse().getContentAsString().contains("Tester"), "last names are never shown");

        // the requester confirms the donation
        mvc.perform(post("/api/matches/" + m1.getId() + "/donated").param("donated", "true").with(as(requester)))
                .andExpect(status().isOk());

        BloodRequest done = requestRepository.findById(id).orElseThrow();
        assertEquals(RequestStatus.FULFILLED, done.getStatus());
        assertNotNull(done.getClosedAt());

        DonorProfile d1 = donorRepository.findById(donor1.getId()).orElseThrow();
        assertEquals(3, d1.getTotalDonations());           // was 2
        assertEquals(9, d1.getLivesHelpedEstimate());      // was 6, +3
        assertEquals(1, count("SELECT COUNT(*) FROM donation_records WHERE donor_id = ? AND source = 'MATCH' AND request_id = ?", donor1.getId(), id));
        assertEquals(1, count("SELECT COUNT(*) FROM donor_deferrals WHERE donor_id = ? AND reason = 'RECENT_DONATION'", donor1.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'REQUEST_FULFILLED'", requester.getId()));

        // the pending donor is told they need not go; the contact is gone from the requester's view
        assertEquals(1, count("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'REQUEST_CLOSED'", donorUser2.getId()));
        MvcResult after = mvc.perform(get("/api/requests/" + id + "/matches").with(as(requester))).andReturn();
        assertFalse(after.getResponse().getContentAsString().contains(donorUser1.getPhoneNumber()));

        // donor view: history and eligibility
        MvcResult hist = mvc.perform(get("/api/donors/me/donations").with(as(donorUser1))).andExpect(status().isOk()).andReturn();
        assertEquals(1, data(hist).size());
        assertEquals("MATCH", data(hist).get(0).get("source").asText());

        MvcResult elig = mvc.perform(get("/api/donors/me/eligibility").with(as(donorUser1))).andExpect(status().isOk()).andReturn();
        JsonNode e = data(elig);
        assertFalse(e.get("eligible").asBoolean());
        assertEquals("RECENT_DONATION", e.get("reasons").get(0).asText());
        assertEquals(clock.today().plusMonths(4).toString(), e.get("nextEligibleDate").asText());
        assertEquals(4, e.get("rules").get("donationIntervalMonths").asInt());

        // recording twice is refused
        mvc.perform(post("/api/matches/" + m1.getId() + "/donated").param("donated", "true").with(as(requester)))
                .andExpect(status().isConflict());
        // a closed request cannot be fulfilled again
        MvcResult again = mvc.perform(post("/api/requests/" + id + "/fulfil").with(as(requester))).andReturn();
        assertEquals(409, again.getResponse().getStatus());
        assertEquals("REQUEST_ALREADY_CLOSED", json(again).get("code").asText());
        assertNotNull(m2);
    }

    @Test
    @DisplayName("A request for two units stays MATCHED after the first confirmed donation and is FULFILLED after the second")
    void twoUnitRequest_fulfilledOnlyWhenConfirmedDonationsReachUnits() throws Exception {
        stubCandidates(donor1, donor2);
        long id = createOk(requester, 2);
        List<RequestMatch> ms = matchRepository.findByRequestIdOrderByDistanceKmAscIdAsc(id);

        mvc.perform(post("/api/matches/" + ms.get(0).getId() + "/respond").param("response", "ACCEPTED").with(as(donorUser1))).andExpect(status().isOk());
        assertEquals(RequestStatus.ESCALATING, requestRepository.findById(id).orElseThrow().getStatus(), "one of two units covered is not MATCHED");
        mvc.perform(post("/api/matches/" + ms.get(1).getId() + "/respond").param("response", "ACCEPTED").with(as(donorUser2))).andExpect(status().isOk());
        assertEquals(RequestStatus.MATCHED, requestRepository.findById(id).orElseThrow().getStatus());

        mvc.perform(post("/api/matches/" + ms.get(0).getId() + "/donated").param("donated", "true").with(as(requester))).andExpect(status().isOk());
        assertEquals(RequestStatus.MATCHED, requestRepository.findById(id).orElseThrow().getStatus());
        mvc.perform(post("/api/matches/" + ms.get(1).getId() + "/donated").param("donated", "true").with(as(requester))).andExpect(status().isOk());
        assertEquals(RequestStatus.FULFILLED, requestRepository.findById(id).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("A no-show on a MATCHED request re-opens it and lowers the donor's reliability")
    void noShow_reopensRequest() throws Exception {
        stubCandidates(donor1);
        long id = createOk(requester, 1);
        RequestMatch m = matchRepository.findByRequestIdOrderByDistanceKmAscIdAsc(id).get(0);
        mvc.perform(post("/api/matches/" + m.getId() + "/respond").param("response", "ACCEPTED").with(as(donorUser1))).andExpect(status().isOk());
        assertEquals(RequestStatus.MATCHED, requestRepository.findById(id).orElseThrow().getStatus());

        mvc.perform(post("/api/matches/" + m.getId() + "/donated").param("donated", "false").with(as(requester))).andExpect(status().isOk());

        assertEquals(RequestStatus.ESCALATING, requestRepository.findById(id).orElseThrow().getStatus());
        assertNull(requestRepository.findById(id).orElseThrow().getMatchedAt());
        assertEquals(0, new java.math.BigDecimal("30.00").compareTo(donorRepository.findById(donor1.getId()).orElseThrow().getReliabilityScore()));
        assertEquals(1, count("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'REQUEST_REOPENED'", requester.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM donation_records"));
    }

    @Test
    @DisplayName("MATCHED requests whose accepted donor was never confirmed re-open after the window, then expire")
    void matched_reopensAfterConfirmationWindow_thenExpires() {
        BloodRequest r = request(requester, RequestStatus.MATCHED, "A+", 1, "Colombo");
        r.setMatchedAt(clock.now().minusHours(5)); // window is 4 h
        requestRepository.save(r);
        RequestMatch accepted = match(r, donor1, MatchStatus.ACCEPTED);

        escalationProcessor.processRequest(r.getId());

        BloodRequest reopened = requestRepository.findById(r.getId()).orElseThrow();
        assertEquals(RequestStatus.ESCALATING, reopened.getStatus());
        assertNull(reopened.getMatchedAt());
        assertEquals(MatchStatus.NO_RESPONSE, matchRepository.findById(accepted.getId()).orElseThrow().getStatus());
        assertEquals(1, count("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'REQUEST_REOPENED'", requester.getId()));
        assertTrue(reopened.getExpiresAt().isAfter(clock.now().plusMinutes(100)), "a re-opened request keeps at least ~2 h");

        // later, when its time is up, it expires (and the fulfilment-rate denominator sees it)
        reopened.setExpiresAt(clock.now().minusMinutes(1));
        requestRepository.save(reopened);
        escalationProcessor.processRequest(r.getId());
        BloodRequest expired = requestRepository.findById(r.getId()).orElseThrow();
        assertEquals(RequestStatus.EXPIRED, expired.getStatus());
        assertNotNull(expired.getClosedAt());
        assertEquals(1, count("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'REQUEST_EXPIRED'", requester.getId()));
    }

    @Test
    void matched_insideTheWindow_isLeftAlone_andPastExpiryExpires() {
        BloodRequest inside = request(requester, RequestStatus.MATCHED, "A+", 1, "Colombo");
        inside.setMatchedAt(clock.now().minusMinutes(30));
        requestRepository.save(inside);
        escalationProcessor.processRequest(inside.getId());
        assertEquals(RequestStatus.MATCHED, requestRepository.findById(inside.getId()).orElseThrow().getStatus());

        BloodRequest late = request(requester, RequestStatus.MATCHED, "B+", 1, "Kandy");
        late.setExpiresAt(clock.now().minusMinutes(5));
        requestRepository.save(late);
        escalationProcessor.processRequest(late.getId());
        assertEquals(RequestStatus.EXPIRED, requestRepository.findById(late.getId()).orElseThrow().getStatus());
    }

    @Test
    void unansweredAlerts_becomeNoResponseAfterTheTimeout() {
        BloodRequest r = request(requester, RequestStatus.ESCALATING, "A+", 1, "Colombo");
        RequestMatch old = matchNotifiedMinutesAgo(r, donor1, MatchStatus.PENDING, 45);
        RequestMatch fresh = matchNotifiedMinutesAgo(r, donor2, MatchStatus.PENDING, 5);

        assertEquals(1, matchTimeoutProcessor.markTimedOutMatches());

        assertEquals(MatchStatus.NO_RESPONSE, matchRepository.findById(old.getId()).orElseThrow().getStatus());
        assertEquals(MatchStatus.PENDING, matchRepository.findById(fresh.getId()).orElseThrow().getStatus());
    }

    // ------------------------------------------------------------------ fulfil, close, ownership

    @Test
    void ownerOrAdmin_canFulfilAndClose_othersCannot() throws Exception {
        BloodRequest r1 = request(requester, RequestStatus.ESCALATING, "A+", 1, "Colombo");
        BloodRequest r2 = request(requester, RequestStatus.OPEN, "B+", 1, "Kandy");
        BloodRequest r3 = request(requester, RequestStatus.MATCHED, "O+", 1, "Galle");

        mvc.perform(post("/api/requests/" + r1.getId() + "/fulfil").with(as(other))).andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/" + r1.getId() + "/close").with(as(other))).andExpect(status().isForbidden());

        MvcResult fulfilled = mvc.perform(post("/api/requests/" + r1.getId() + "/fulfil").with(as(requester))).andExpect(status().isOk()).andReturn();
        assertEquals("FULFILLED", data(fulfilled).get("status").asText());
        assertNotNull(requestRepository.findById(r1.getId()).orElseThrow().getClosedAt());

        MvcResult closed = mvc.perform(post("/api/requests/" + r2.getId() + "/close").with(as(requester))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"FOUND_DONOR_ELSEWHERE\"}")).andExpect(status().isOk()).andReturn();
        assertEquals("CANCELLED", data(closed).get("status").asText());
        assertEquals("FOUND_DONOR_ELSEWHERE", requestRepository.findById(r2.getId()).orElseThrow().getCloseReason());

        mvc.perform(post("/api/requests/" + r3.getId() + "/fulfil").with(as(admin))).andExpect(status().isOk());
        assertEquals(RequestStatus.FULFILLED, requestRepository.findById(r3.getId()).orElseThrow().getStatus());

        // the legacy cancel endpoint still works and sets closed_at too
        BloodRequest r4 = request(requester, RequestStatus.ESCALATING, "AB+", 1, "Matara");
        mvc.perform(put("/api/requests/" + r4.getId() + "/cancel").with(as(requester))).andExpect(status().isOk());
        BloodRequest cancelled = requestRepository.findById(r4.getId()).orElseThrow();
        assertEquals(RequestStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getClosedAt());

        // a donor cannot use these endpoints at all
        mvc.perform(post("/api/requests/" + r1.getId() + "/fulfil").with(as(donorUser1))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ access control

    @Test
    @DisplayName("GET /requests/{id}: owner, admin and matched donors only; fraud fields are admin-only")
    void getById_isRestrictedToOwnerAdminAndMatchedDonors() throws Exception {
        BloodRequest r = request(requester, RequestStatus.ESCALATING, "A+", 1, "Colombo");
        r.setFraudRiskScore(42);
        r.setAiFlagReason("test flag");
        requestRepository.save(r);
        match(r, donor1, MatchStatus.PENDING);
        String url = "/api/requests/" + r.getId();

        // unrelated donor and unrelated requester: 404, indistinguishable from a missing id
        mvc.perform(get(url).with(as(donorUser2))).andExpect(status().isNotFound());
        mvc.perform(get(url).with(as(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/requests/999999").with(as(donorUser2))).andExpect(status().isNotFound());

        JsonNode asDonor = data(mvc.perform(get(url).with(as(donorUser1))).andExpect(status().isOk()).andReturn());
        assertEquals(r.getLatitude(), asDonor.get("latitude").asDouble(), 1e-9, "a matched donor needs the exact location");
        assertFalse(asDonor.has("fraudRiskScore") && !asDonor.get("fraudRiskScore").isNull());
        assertFalse(asDonor.has("acceptedCount") && !asDonor.get("acceptedCount").isNull());

        JsonNode asOwner = data(mvc.perform(get(url).with(as(requester))).andExpect(status().isOk()).andReturn());
        assertTrue(asOwner.has("acceptedCount"));
        assertFalse(asOwner.has("fraudRiskScore") && !asOwner.get("fraudRiskScore").isNull());

        JsonNode asAdmin = data(mvc.perform(get(url).with(as(admin))).andExpect(status().isOk()).andReturn());
        assertEquals(42, asAdmin.get("fraudRiskScore").asInt());
        assertEquals("test flag", asAdmin.get("aiFlagReason").asText());
    }

    @Test
    void list_showsDonorsOnlyActiveRequestsWithCoarseLocation_andAdminsEverything() throws Exception {
        BloodRequest active = request(requester, RequestStatus.ESCALATING, "A+", 1, "Colombo");
        active.setLatitude(6.927134);
        active.setLongitude(79.861245);
        requestRepository.save(active);
        request(requester, RequestStatus.OPEN, "B+", 1, "Kandy");
        request(requester, RequestStatus.MATCHED, "O+", 1, "Galle");
        request(requester, RequestStatus.CANCELLED, "AB+", 1, "Matara");
        BloodRequest expiredButUnprocessed = request(other, RequestStatus.ESCALATING, "A-", 1, "Jaffna");
        expiredButUnprocessed.setExpiresAt(clock.now().minusMinutes(2));
        requestRepository.save(expiredButUnprocessed);

        JsonNode forDonor = data(mvc.perform(get("/api/requests").with(as(donorUser1))).andExpect(status().isOk()).andReturn());
        assertEquals(2, forDonor.size(), "only OPEN/ESCALATING requests that have not passed their expiry");
        for (JsonNode n : forDonor) {
            assertTrue(n.get("coarseLocation").asBoolean());
            assertFalse(n.has("fraudRiskScore") && !n.get("fraudRiskScore").isNull());
        }
        JsonNode colombo = null;
        for (JsonNode n : forDonor) if (n.get("district").asText().equals("Colombo")) colombo = n;
        assertNotNull(colombo);
        assertEquals(6.93, colombo.get("latitude").asDouble(), 1e-9);
        assertEquals(79.86, colombo.get("longitude").asDouble(), 1e-9);

        JsonNode forAdmin = data(mvc.perform(get("/api/requests").with(as(admin))).andExpect(status().isOk()).andReturn());
        assertEquals(5, forAdmin.size());
        assertFalse(forAdmin.get(0).get("coarseLocation").asBoolean());
    }

    // ------------------------------------------------------------------ reports

    @Test
    void reports_onePerUserPerRequest_notOnOwnRequests() throws Exception {
        BloodRequest r = request(requester, RequestStatus.ESCALATING, "A+", 1, "Colombo");
        String url = "/api/requests/" + r.getId() + "/report";
        String body = "{\"reason\":\"FAKE_OR_MISLEADING\",\"details\":\"No such patient\"}";

        mvc.perform(post(url).with(as(donorUser1)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertEquals(1, count("SELECT COUNT(*) FROM request_reports WHERE request_id = ? AND reason_code = 'FAKE_OR_MISLEADING'", r.getId()));
        assertEquals("FAKE_OR_MISLEADING: No such patient", jdbc.queryForObject("SELECT reason FROM request_reports WHERE request_id = ?", String.class, r.getId()));

        MvcResult dup = mvc.perform(post(url).with(as(donorUser1)).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(409, dup.getResponse().getStatus());
        assertEquals("REPORT_ALREADY_FILED", json(dup).get("code").asText());

        // another user can still report
        mvc.perform(post(url).with(as(donorUser2)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}")).andExpect(status().isOk());
        assertEquals(2, count("SELECT COUNT(*) FROM request_reports WHERE request_id = ?", r.getId()));

        // not your own request, not an unknown reason, details are capped
        mvc.perform(post(url).with(as(requester)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(url).with(as(other)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"BOGUS\"}")).andExpect(status().isBadRequest());
        String tooLong = "{\"reason\":\"OTHER\",\"details\":\"" + "x".repeat(501) + "\"}";
        mvc.perform(post(url).with(as(other)).contentType(MediaType.APPLICATION_JSON).content(tooLong)).andExpect(status().isBadRequest());
        // unauthenticated
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        // unknown request
        mvc.perform(post("/api/requests/999999/report").with(as(other)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ requester view of matches

    @Test
    void matchesEndpoint_isOwnerOrAdminOnly_andHidesContactUnlessAccepted() throws Exception {
        BloodRequest r = request(requester, RequestStatus.ESCALATING, "A+", 2, "Colombo");
        User u3 = user("donorthree", Role.DONOR, VerificationStatus.VERIFIED);
        User u4 = user("donorfour", Role.DONOR, VerificationStatus.VERIFIED);
        match(r, donor1, MatchStatus.ACCEPTED);
        match(r, donor2, MatchStatus.DECLINED);
        match(r, donor(u3, "A+", "Colombo"), MatchStatus.NO_RESPONSE);
        match(r, donor(u4, "A+", "Colombo"), MatchStatus.PENDING);
        String url = "/api/requests/" + r.getId() + "/matches";

        mvc.perform(get(url).with(as(other))).andExpect(status().isNotFound());
        mvc.perform(get(url).with(as(donorUser1))).andExpect(status().isForbidden());

        for (User viewer : List.of(requester, admin)) {
            MvcResult res = mvc.perform(get(url).with(as(viewer))).andExpect(status().isOk()).andReturn();
            JsonNode matches = data(res).get("matches");
            assertEquals(4, matches.size());
            int withContact = 0;
            for (JsonNode m : matches) {
                if (m.has("contact")) {
                    withContact++;
                    assertEquals("ACCEPTED", m.get("status").asText());
                }
                assertTrue(m.has("donorFirstName") && m.has("bloodType") && m.has("distanceKm") && m.has("status"));
            }
            assertEquals(1, withContact, "exactly the accepted donor has a contact");
            String raw = res.getResponse().getContentAsString();
            for (User hidden : List.of(donorUser2, u3, u4)) assertFalse(raw.contains(hidden.getPhoneNumber()), "phone of a non-accepted donor leaked");
            assertFalse(raw.contains(donorUser1.getEmail()));
            assertEquals(1, data(res).get("acceptedCount").asInt());
        }
    }

    @Test
    void donorsOwnMatchList_showsDistance_andRespondIsDonorOnly() throws Exception {
        BloodRequest r = request(requester, RequestStatus.ESCALATING, "A+", 1, "Colombo");
        match(r, donor1, MatchStatus.PENDING);

        JsonNode mine = data(mvc.perform(get("/api/donors/me/matches").with(as(donorUser1))).andExpect(status().isOk()).andReturn());
        assertEquals(1, mine.size());
        assertEquals(2.5, mine.get(0).get("distanceKm").asDouble(), 1e-9);
        assertEquals("Base Hospital Colombo", mine.get(0).get("request").get("hospitalName").asText());

        RequestMatch m = matchRepository.findByDonorId(donor1.getId()).get(0);
        mvc.perform(post("/api/matches/" + m.getId() + "/respond").param("response", "ACCEPTED").with(as(requester))).andExpect(status().isForbidden());
        mvc.perform(post("/api/matches/" + m.getId() + "/respond").param("response", "ACCEPTED").with(as(donorUser2))).andExpect(status().isForbidden());
        mvc.perform(post("/api/matches/" + m.getId() + "/respond").param("response", "PENDING").with(as(donorUser1))).andExpect(status().isBadRequest());
    }

    @Test
    void donorProfile_validation_normalisesDistrict_andRejectsBadValues() throws Exception {
        User fresh = user("freshdonor", Role.DONOR, VerificationStatus.VERIFIED);
        LocalDate dob = LocalDate.now().minusYears(30);
        String ok = "{\"bloodType\":\"O+\",\"dateOfBirth\":\"" + dob + "\",\"weightKg\":68,\"district\":\"kandy DISTRICT\",\"latitude\":7.29,\"longitude\":80.63}";
        mvc.perform(put("/api/donors/me").with(as(fresh)).contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isOk());
        assertEquals("Kandy", donorRepository.findByUserId(fresh.getId()).orElseThrow().getDistrict());

        String future = ok.replace(dob.toString(), LocalDate.now().plusDays(3).toString());
        mvc.perform(put("/api/donors/me").with(as(fresh)).contentType(MediaType.APPLICATION_JSON).content(future)).andExpect(status().isBadRequest());
        String light = ok.replace("\"weightKg\":68", "\"weightKg\":50");
        mvc.perform(put("/api/donors/me").with(as(fresh)).contentType(MediaType.APPLICATION_JSON).content(light)).andExpect(status().isBadRequest());
        String lightest = ok.replace("\"weightKg\":68", "\"weightKg\":35");
        mvc.perform(put("/api/donors/me").with(as(fresh)).contentType(MediaType.APPLICATION_JSON).content(lightest)).andExpect(status().isBadRequest());
        String nowhere = ok.replace("kandy DISTRICT", "Atlantis");
        MvcResult res = mvc.perform(put("/api/donors/me").with(as(fresh)).contentType(MediaType.APPLICATION_JSON).content(nowhere)).andReturn();
        assertEquals(400, res.getResponse().getStatus());
        assertEquals("UNKNOWN_DISTRICT", json(res).get("code").asText());
        String abroad = ok.replace("\"latitude\":7.29,\"longitude\":80.63", "\"latitude\":13.08,\"longitude\":80.27");
        mvc.perform(put("/api/donors/me").with(as(fresh)).contentType(MediaType.APPLICATION_JSON).content(abroad)).andExpect(status().isBadRequest());
    }

    @Test
    void metaDistricts_listsTheTwentyFiveOfficialNames() throws Exception {
        MvcResult res = mvc.perform(get("/api/meta/districts").with(as(donorUser1))).andExpect(status().isOk()).andReturn();
        JsonNode d = data(res);
        assertEquals(25, d.get("districts").size());
        assertEquals("Colombo", d.get("districts").get(0).get("name").asText());
        assertEquals(5.7, d.get("bounds").get("minLat").asDouble(), 1e-9);
        assertNotNull(Map.of());
        assertNotNull(LocalDateTime.now());
    }
}
