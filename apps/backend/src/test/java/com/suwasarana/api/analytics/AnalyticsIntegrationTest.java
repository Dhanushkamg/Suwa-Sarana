package com.suwasarana.api.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.suwasarana.api.analytics.dto.DistrictSummaryDto;
import com.suwasarana.api.analytics.dto.NationalSummaryDto;
import com.suwasarana.api.donor.DonorDeferral;
import com.suwasarana.api.donor.DeferralRepository;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.matching.MatchStatus;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.testsupport.LifecycleIntegrationBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fixtures with known answers; every number the API returns is also compared with a plain SQL count over the same
 * tables, so a wrong definition shows up as a mismatch with the database.
 */
class AnalyticsIntegrationTest extends LifecycleIntegrationBase {

    @Autowired private AnalyticsService analytics;
    @Autowired private DeferralRepository deferralRepository;

    private User owner;
    private User admin;
    private User donorViewer;

    @BeforeEach
    void fixtures() {
        owner = user("owner", Role.REQUESTER, VerificationStatus.VERIFIED);
        admin = user("admin", Role.ADMIN, VerificationStatus.VERIFIED);
        donorViewer = user("viewer", Role.DONOR, VerificationStatus.VERIFIED);

        // ---- Colombo, including legacy spellings of the district
        req("Colombo", RequestStatus.OPEN, 3, +120, 0);
        BloodRequest matched = req("Colombo", RequestStatus.MATCHED, 2, +120, 0);
        req("Colombo", RequestStatus.ESCALATING, 1, -10, 0);             // past its expiry but not yet processed: not active
        for (int i = 0; i < 3; i++) req("Colombo", RequestStatus.FULFILLED, 1, -60, 2);
        req("Colombo", RequestStatus.EXPIRED, 1, -60, 3);
        req("Colombo", RequestStatus.CANCELLED, 1, -60, 5);
        req("Colombo", RequestStatus.FULFILLED, 1, -60, 40);            // closed outside the 30-day window
        req("colombo district", RequestStatus.ESCALATING, 1, +120, 0);  // same district, different text
        req("colombo district", RequestStatus.FULFILLED, 1, -60, 1);
        // two accepted donors cover the matched request completely
        User m1 = user("m1", Role.DONOR, VerificationStatus.VERIFIED);
        User m2 = user("m2", Role.DONOR, VerificationStatus.VERIFIED);
        match(matched, donor(m1, "A+", "Colombo"), MatchStatus.ACCEPTED);
        match(matched, donor(m2, "A+", "Colombo"), MatchStatus.ACCEPTED);

        // ---- Kandy: one open request, nothing closed yet
        req("Kandy", RequestStatus.OPEN, 2, +120, 0);
        // ---- Galle: only fulfilled requests
        req("Galle", RequestStatus.FULFILLED, 1, -60, 1);
        req("Galle", RequestStatus.FULFILLED, 1, -60, 1);
        // ---- a district name that matches none of the 25
        req("Atlantis", RequestStatus.ESCALATING, 1, +120, 0);
        req("Atlantis", RequestStatus.CANCELLED, 1, -60, 1);

        // ---- donors: Colombo has m1, m2 (eligible, available) plus the cases below
        DonorProfile d3 = donor(user("d3", Role.DONOR, VerificationStatus.VERIFIED), "O+", "Colombo");
        d3.setAvailable(false);
        donorRepository.save(d3);
        DonorProfile light = donor(user("d4", Role.DONOR, VerificationStatus.VERIFIED), "O+", "Colombo");
        light.setWeightKg(new BigDecimal("50.00"));                      // must be above 50
        donorRepository.save(light);
        DonorProfile deferred = donor(user("d5", Role.DONOR, VerificationStatus.VERIFIED), "O+", "Colombo");
        DonorDeferral f = new DonorDeferral();
        f.setDonor(deferred);
        f.setReason("RECENT_DONATION");
        f.setDeferredFrom(LocalDate.now().minusDays(20));
        f.setDeferredUntil(LocalDate.now().plusDays(30));
        deferralRepository.save(f);
        DonorProfile old = donor(user("d6", Role.DONOR, VerificationStatus.VERIFIED), "O+", "Colombo");
        old.setDateOfBirth(LocalDate.now().minusYears(57));              // first-time donors only up to 55
        old.setTotalDonations(0);
        donorRepository.save(old);
        donor(user("g1", Role.DONOR, VerificationStatus.VERIFIED), "B+", "Gampaha");
        // two donors who registered but never completed a profile
        user("noprofile1", Role.DONOR, VerificationStatus.PENDING);
        user("noprofile2", Role.DONOR, VerificationStatus.PENDING);
    }

    /** minutesToExpiry may be negative; closedDaysAgo 0 = still open. */
    private BloodRequest req(String district, RequestStatus status, int units, int minutesToExpiry, int closedDaysAgo) {
        LocalDateTime now = clock.utcNow();
        BloodRequest r = new BloodRequest();
        r.setRequester(owner);
        r.setPatientBloodType("A+");
        r.setUnitsNeeded((short) units);
        r.setUrgency(com.suwasarana.api.request.Urgency.URGENT);
        r.setHospitalName("Hospital");
        r.setDistrict(district);
        r.setLatitude(6.93);
        r.setLongitude(79.86);
        r.setStatus(status);
        r.setCreatedAt(now.minusDays(Math.max(closedDaysAgo, 1)));
        r.setExpiresAt(now.plusMinutes(minutesToExpiry));
        if (!status.isActive()) r.setClosedAt(now.minusDays(closedDaysAgo));
        return requestRepository.save(r);
    }

    private DistrictSummaryDto find(List<DistrictSummaryDto> all, String name) {
        return all.stream().filter(d -> d.getDistrict().equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("District figures follow the definitions: normalised names, windowed rates, null instead of fake 100 % or 0 %")
    void districtFigures() {
        List<DistrictSummaryDto> all = analytics.districtSummaries(true);
        assertEquals(25, all.size());
        assertEquals(25, all.stream().map(DistrictSummaryDto::getDistrict).distinct().count());

        DistrictSummaryDto colombo = find(all, "Colombo");
        assertEquals(3, colombo.getActiveRequests(), "OPEN + MATCHED + the 'colombo district' ESCALATING one; the lapsed one is not active");
        assertEquals(1, colombo.getAwaitingRequests());
        assertEquals(4, colombo.getFulfilledRequests(), "3 + 1 variant, not the one closed 40 days ago");
        assertEquals(1L, colombo.getExpiredRequests());
        assertEquals(1L, colombo.getCancelledRequests());
        assertEquals(6, colombo.getClosedRequests());
        assertEquals(66.7, colombo.getFulfillmentRate());
        assertEquals(11, colombo.getTotalRequests());
        assertEquals(6, colombo.getRegisteredDonors());
        assertEquals(6, colombo.getDonorCount());
        assertEquals(2, colombo.getAvailableDonors(), "unavailable, 50 kg, deferred and over-55 first-timer are not available");
        assertEquals(4L, colombo.getUnitsOutstanding(), "3 (open) + 0 (matched, covered by 2 accepted) + 1");
        assertEquals("CRITICAL", colombo.getShortageLevel());
        assertTrue(colombo.isHasData());

        DistrictSummaryDto kandy = find(all, "Kandy");
        assertEquals(1, kandy.getActiveRequests());
        assertEquals(0, kandy.getClosedRequests());
        assertNull(kandy.getFulfillmentRate(), "nothing closed in the window: no rate, not 0 % and not 100 %");

        DistrictSummaryDto galle = find(all, "Galle");
        assertEquals(100.0, galle.getFulfillmentRate());
        assertEquals(0, galle.getActiveRequests());

        DistrictSummaryDto gampaha = find(all, "Gampaha");
        assertEquals(1, gampaha.getRegisteredDonors());
        assertEquals(1, gampaha.getAvailableDonors());
        assertEquals("BALANCED", gampaha.getShortageLevel());

        DistrictSummaryDto jaffna = find(all, "Jaffna");
        assertFalse(jaffna.isHasData());
        assertNull(jaffna.getFulfillmentRate());
        assertEquals("NO_DATA", jaffna.getShortageLevel());
        assertEquals(0, jaffna.getActiveRequests());
    }

    @Test
    @DisplayName("National figures come from database-wide totals, not from an average of district rates")
    void nationalFiguresArePooled() {
        NationalSummaryDto n = analytics.nationalSummary(true);

        assertEquals(5, n.getActiveRequests(), "Colombo 3 + Kandy 1 + the unmapped Atlantis one");
        assertEquals(1, n.getAwaitingRequests());
        assertEquals(6, n.getFulfilledRequests());
        assertEquals(1L, n.getExpiredRequests());
        assertEquals(2L, n.getCancelledRequests(), "Colombo 1 + Atlantis 1: unmapped rows still count nationally");
        assertEquals(9, n.getClosedRequests());
        assertEquals(66.7, n.getFulfillmentRate(), "6 / 9 pooled; the mean of the district rates (66.7 and 100) would be 83.3");
        assertEquals(1L, n.getUnmappedRequests() - 1, "Atlantis has two requests");
        assertEquals(7L, n.getUnitsOutstanding());

        assertEquals(10, n.getRegisteredDonors(), "users with role DONOR: 7 with a profile, 3 without (two registered-only plus the viewer)");
        assertEquals(7, n.getDonorsWithProfile());
        assertEquals(3, n.getAvailableDonors());
        assertEquals(4, n.getDistrictsWithData());
        assertEquals(25, n.getDistrictsTotal());
        assertEquals(30, n.getWindowDays());
    }

    @Test
    @DisplayName("Every number equals a plain SQL count over the same tables")
    void numbersEqualTheDatabase() {
        LocalDateTime now = clock.utcNow();
        Timestamp nowTs = Timestamp.valueOf(now);
        Timestamp windowStart = Timestamp.valueOf(now.minusDays(30));
        NationalSummaryDto n = analytics.nationalSummary(true);

        assertEquals(count("SELECT COUNT(*) FROM blood_requests WHERE status IN ('OPEN','ESCALATING','MATCHED') AND expires_at > ?", nowTs), n.getActiveRequests());
        assertEquals(count("SELECT COUNT(*) FROM blood_requests WHERE status = 'MATCHED' AND expires_at > ?", nowTs), n.getAwaitingRequests());
        assertEquals(count("SELECT COUNT(*) FROM blood_requests WHERE status = 'FULFILLED' AND closed_at >= ?", windowStart), n.getFulfilledRequests());
        assertEquals(count("SELECT COUNT(*) FROM blood_requests WHERE status = 'EXPIRED' AND closed_at >= ?", windowStart), n.getExpiredRequests());
        assertEquals(count("SELECT COUNT(*) FROM blood_requests WHERE status = 'CANCELLED' AND closed_at >= ?", windowStart), n.getCancelledRequests());
        assertEquals(count("SELECT COUNT(*) FROM users WHERE role = 'DONOR'"), n.getRegisteredDonors());
        assertEquals(count("SELECT COUNT(*) FROM donor_profiles"), n.getDonorsWithProfile());

        // the district rows plus the unmapped bucket account for every request in the table
        List<DistrictSummaryDto> all = analytics.districtSummaries(true);
        long inDistricts = all.stream().mapToLong(DistrictSummaryDto::getTotalRequests).sum();
        assertEquals(count("SELECT COUNT(*) FROM blood_requests"), inDistricts + n.getUnmappedRequests());
        assertEquals(n.getActiveRequests() - 1, all.stream().mapToLong(DistrictSummaryDto::getActiveRequests).sum());
        assertEquals(count("SELECT COUNT(*) FROM donor_profiles"), all.stream().mapToLong(DistrictSummaryDto::getRegisteredDonors).sum());
        assertEquals(n.getAvailableDonors(), all.stream().mapToLong(DistrictSummaryDto::getAvailableDonors).sum());
    }

    @Test
    void emptyDatabase_hasNoFakeNumbers() {
        cleanDatabase();
        NationalSummaryDto n = analytics.nationalSummary(false);
        assertEquals(0, n.getActiveRequests());
        assertEquals(0, n.getClosedRequests());
        assertNull(n.getFulfillmentRate(), "no data means no rate");
        assertEquals(0, n.getRegisteredDonors());
        assertEquals(0, n.getDistrictsWithData());
        for (DistrictSummaryDto d : analytics.districtSummaries(false)) {
            assertNull(d.getFulfillmentRate(), d.getDistrict());
            assertEquals("NO_DATA", d.getShortageLevel(), d.getDistrict());
        }
        assertTrue(analytics.activeRequestsByDistrict().isEmpty(), "no hard-coded fallback districts");
    }

    @Test
    @DisplayName("Admin and shared endpoints return the same numbers; the shared one omits the admin-only fields")
    void endpoints() throws Exception {
        JsonNode adminRows = data(mvc.perform(get("/api/admin/analytics/district-summary").with(as(admin))).andExpect(status().isOk()).andReturn());
        JsonNode publicRows = data(mvc.perform(get("/api/analytics/district-summary").with(as(donorViewer))).andExpect(status().isOk()).andReturn());
        assertEquals(25, adminRows.size());
        assertEquals(25, publicRows.size());
        for (int i = 0; i < 25; i++) {
            JsonNode a = adminRows.get(i);
            JsonNode p = publicRows.get(i);
            assertEquals(a.get("district").asText(), p.get("district").asText());
            for (String field : List.of("activeRequests", "awaitingRequests", "fulfilledRequests", "closedRequests", "totalRequests",
                    "registeredDonors", "availableDonors", "donorCount", "shortageLevel")) {
                assertEquals(a.get(field), p.get(field), a.get("district").asText() + "." + field);
            }
            assertEquals(a.get("fulfillmentRate"), p.get("fulfillmentRate"));
            assertTrue(a.has("unitsOutstanding") && a.has("expiredRequests") && a.has("cancelledRequests"));
            assertFalse(p.has("unitsOutstanding") || p.has("expiredRequests") || p.has("cancelledRequests"), "not for the public view");
        }
        // a null rate is present in the JSON as null (the UI shows "no data")
        JsonNode kandy = null;
        for (JsonNode r : publicRows) if (r.get("district").asText().equals("Kandy")) kandy = r;
        assertTrue(kandy.has("fulfillmentRate") && kandy.get("fulfillmentRate").isNull());

        JsonNode nPublic = data(mvc.perform(get("/api/analytics/summary").with(as(donorViewer))).andExpect(status().isOk()).andReturn());
        JsonNode nAdmin = data(mvc.perform(get("/api/admin/analytics/summary").with(as(admin))).andExpect(status().isOk()).andReturn());
        assertEquals(nAdmin.get("activeRequests"), nPublic.get("activeRequests"));
        assertEquals(66.7, nPublic.get("fulfillmentRate").asDouble());
        assertEquals(10, nPublic.get("registeredDonors").asInt());
        assertFalse(nPublic.has("unitsOutstanding"));
        assertEquals(7, nAdmin.get("unitsOutstanding").asInt());

        // the admin overview map: active requests per district, nothing invented
        JsonNode map = data(mvc.perform(get("/api/admin/analytics").with(as(admin))).andExpect(status().isOk()).andReturn());
        assertEquals(3, map.get("Colombo").asInt());
        assertEquals(1, map.get("Kandy").asInt());
        assertEquals(1, map.get("Unmapped").asInt());
        assertFalse(map.has("Gampaha"));
        assertFalse(map.has("Galle"));

        // admin endpoints stay admin-only
        mvc.perform(get("/api/admin/analytics/district-summary").with(as(donorViewer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/analytics/summary").with(as(donorViewer))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/analytics").with(as(owner))).andExpect(status().isForbidden());
        MvcResult anonymous = mvc.perform(get("/api/analytics/summary")).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());
        assertEquals(Map.of().size(), 0);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
