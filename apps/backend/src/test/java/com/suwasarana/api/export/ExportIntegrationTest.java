package com.suwasarana.api.export;

import com.suwasarana.api.donor.DonationRecord;
import com.suwasarana.api.donor.DonationRecordRepository;
import com.suwasarana.api.donor.DonationSource;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExportIntegrationTest extends LifecycleIntegrationBase {

    @Autowired private DonationRecordRepository donationRecords;

    private User requester;
    private User otherRequester;
    private User admin;
    private User donorUser;
    private User otherDonorUser;
    private DonorProfile donor;
    private DonorProfile otherDonor;

    @BeforeEach
    void fixtures() {
        requester = user("requester", Role.REQUESTER, VerificationStatus.VERIFIED);
        otherRequester = user("otherrequester", Role.HOSPITAL_REQUESTER, VerificationStatus.VERIFIED);
        admin = user("admin", Role.ADMIN, VerificationStatus.VERIFIED);
        donorUser = user("donor", Role.DONOR, VerificationStatus.VERIFIED);
        otherDonorUser = user("otherdonor", Role.DONOR, VerificationStatus.VERIFIED);
        donor = donor(donorUser, "O+", "Colombo");
        otherDonor = donor(otherDonorUser, "A+", "Kandy");
    }

    private String csv(MvcResult r) throws Exception {
        byte[] bytes = r.getResponse().getContentAsByteArray();
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, java.util.Arrays.copyOfRange(bytes, 0, 3), "UTF-8 BOM");
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    private List<String> lines(String csv) {
        return csv.isBlank() ? List.of() : List.of(csv.split("\r\n"));
    }

    private DonationRecord donation(DonorProfile d, User host, String hostName, LocalDate on) {
        DonationRecord r = new DonationRecord();
        r.setDonorId(d.getId());
        r.setDonatedOn(on);
        r.setSource(DonationSource.MATCH);
        r.setHostUserId(host.getId());
        r.setHostName(hostName);
        r.setDistrict("Colombo");
        r.setBloodType(d.getBloodType());
        return donationRecords.save(r);
    }

    @Test
    @DisplayName("requests.csv: own rows only, UTF-8 BOM, CRLF, attachment headers, no coordinates")
    void requestsCsv_ownRows() throws Exception {
        request(requester, RequestStatus.ESCALATING, "A+", 1, "Colombo");
        request(requester, RequestStatus.FULFILLED, "B+", 2, "Kandy");
        request(otherRequester, RequestStatus.OPEN, "O-", 1, "Galle");

        MvcResult res = mvc.perform(get("/api/exports/requests.csv").with(as(requester))).andExpect(status().isOk()).andReturn();

        assertTrue(res.getResponse().getContentType().startsWith("text/csv"));
        assertTrue(res.getResponse().getContentType().toLowerCase().contains("utf-8"));
        assertTrue(res.getResponse().getHeader("Content-Disposition").contains("attachment"));
        assertTrue(res.getResponse().getHeader("Content-Disposition").contains("requests.csv"));
        List<String> rows = lines(csv(res));
        assertEquals(3, rows.size(), "header + the requester's two requests");
        assertTrue(rows.get(0).startsWith("request_id,created_at,status,urgency,blood_type,units_needed,hospital,district,closed_at,close_reason"));
        assertFalse(rows.get(0).contains("requester_id") || rows.get(0).contains("latitude"), "no ids of others and no coordinates");
        assertFalse(String.join("\n", rows).contains("Galle"), "another user's request must not appear");
        assertTrue(String.join("\n", rows).contains("FULFILLED"));
    }

    @Test
    void requestsCsv_adminSeesAll_donorSeesMatchedOnly_anonymousIsRefused() throws Exception {
        BloodRequest mine = request(requester, RequestStatus.ESCALATING, "A+", 1, "Colombo");
        request(otherRequester, RequestStatus.OPEN, "O-", 1, "Galle");
        match(mine, donor, MatchStatus.PENDING);

        List<String> adminRows = lines(csv(mvc.perform(get("/api/exports/requests.csv").with(as(admin))).andExpect(status().isOk()).andReturn()));
        assertEquals(3, adminRows.size());
        assertTrue(adminRows.get(0).endsWith("requester_id,fraud_risk_score"));

        List<String> donorRows = lines(csv(mvc.perform(get("/api/exports/requests.csv").with(as(donorUser))).andExpect(status().isOk()).andReturn()));
        assertEquals(2, donorRows.size(), "only the request this donor was alerted about");
        assertFalse(donorRows.get(1).contains("Galle"));
        assertEquals(1, lines(csv(mvc.perform(get("/api/exports/requests.csv").with(as(otherDonorUser))).andReturn())).size(), "header only");

        mvc.perform(get("/api/exports/requests.csv")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Free text that looks like a formula is neutralised; commas and quotes are escaped")
    void csvInjectionIsNeutralised() throws Exception {
        BloodRequest r = request(requester, RequestStatus.OPEN, "A+", 1, "Colombo");
        r.setHospitalName("=HYPERLINK(\"http://evil.example\",\"click\")");
        requestRepository.save(r);
        BloodRequest r2 = request(requester, RequestStatus.OPEN, "B+", 1, "Kandy");
        r2.setHospitalName("@SUM(1+1)*cmd|' /C calc'!A0");
        requestRepository.save(r2);
        BloodRequest r3 = request(requester, RequestStatus.OPEN, "O+", 1, "Galle");
        r3.setHospitalName("Teaching Hospital, \"Karapitiya\"");
        requestRepository.save(r3);

        String body = csv(mvc.perform(get("/api/exports/requests.csv").with(as(requester))).andReturn());

        assertTrue(body.contains("\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"click\"\")\""), body);
        assertTrue(body.contains("'@SUM(1+1)*cmd|' /C calc'!A0"), body);
        assertTrue(body.contains("\"Teaching Hospital, \"\"Karapitiya\"\"\""), body);
        for (String line : lines(body)) {
            for (String cell : line.split(",")) {
                assertFalse(cell.startsWith("=") || cell.startsWith("@"), "dangerous cell leaked: " + cell);
            }
        }
    }

    @Test
    @DisplayName("Large exports are streamed in pages and come out complete")
    void largeExport_isComplete() throws Exception {
        List<BloodRequest> batch = new ArrayList<>();
        for (int i = 0; i < 1203; i++) {
            BloodRequest r = new BloodRequest();
            r.setRequester(requester);
            r.setPatientBloodType("A+");
            r.setUnitsNeeded((short) 1);
            r.setUrgency(com.suwasarana.api.request.Urgency.ROUTINE);
            r.setHospitalName("Hospital " + i);
            r.setDistrict("Colombo");
            r.setLatitude(6.9);
            r.setLongitude(79.8);
            r.setStatus(RequestStatus.EXPIRED);
            r.setCreatedAt(clock.utcNow().minusDays(2));
            r.setExpiresAt(clock.utcNow().minusDays(1));
            r.setClosedAt(clock.utcNow().minusDays(1));
            batch.add(r);
        }
        requestRepository.saveAll(batch);

        List<String> rows = lines(csv(mvc.perform(get("/api/exports/requests.csv").with(as(requester))).andReturn()));
        assertEquals(1204, rows.size());
        assertEquals(1203, rows.stream().skip(1).map(l -> l.split(",")[0]).distinct().count(), "no row repeated or skipped across pages");

        // date filter
        String tomorrow = LocalDate.now().plusDays(1).toString();
        assertEquals(1, lines(csv(mvc.perform(get("/api/exports/requests.csv").param("from", tomorrow).with(as(requester))).andReturn())).size());
    }

    @Test
    void donationsCsv_scopedByRole() throws Exception {
        donation(donor, requester, "Base Hospital Colombo", LocalDate.of(2026, 3, 14));
        donation(donor, otherRequester, "=cmd", LocalDate.of(2026, 5, 2));
        donation(otherDonor, requester, "Base Hospital Colombo", LocalDate.of(2026, 6, 1));

        List<String> own = lines(csv(mvc.perform(get("/api/exports/donations.csv").with(as(donorUser))).andExpect(status().isOk()).andReturn()));
        assertEquals(3, own.size(), "header + the donor's two donations");
        assertTrue(own.get(0).startsWith("donation_id,donated_on,source,blood_type,host,district,request_id"));
        assertFalse(own.get(0).contains("donor_id"));
        assertTrue(String.join("\n", own).contains("'=cmd"), "host name is neutralised");

        List<String> host = lines(csv(mvc.perform(get("/api/exports/donations.csv").with(as(requester))).andReturn()));
        assertEquals(3, host.size(), "donations the requester hosted: two");
        assertFalse(String.join("\n", host).contains("=cmd"));

        List<String> all = lines(csv(mvc.perform(get("/api/exports/donations.csv").with(as(admin))).andReturn()));
        assertEquals(4, all.size());
        assertTrue(all.get(0).endsWith("donor_id,host_user_id"));

        List<String> ranged = lines(csv(mvc.perform(get("/api/exports/donations.csv").param("from", "2026-05-01").param("to", "2026-05-31").with(as(admin))).andReturn()));
        assertEquals(2, ranged.size());
        mvc.perform(get("/api/exports/donations.csv")).andExpect(status().isUnauthorized());
    }
}
