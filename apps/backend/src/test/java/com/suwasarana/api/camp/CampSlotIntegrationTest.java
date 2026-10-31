package com.suwasarana.api.camp;

import com.fasterxml.jackson.databind.JsonNode;
import com.suwasarana.api.donor.DeferralRepository;
import com.suwasarana.api.donor.DonorDeferral;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.slot.BookingStatus;
import com.suwasarana.api.slot.DonationSlot;
import com.suwasarana.api.slot.DonationSlotRepository;
import com.suwasarana.api.slot.SlotBooking;
import com.suwasarana.api.slot.SlotBookingRepository;
import com.suwasarana.api.slot.SlotStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Camp and slot rules, run with open-in-view off so any lazy read outside a transaction fails here (as it would in production). */
class CampSlotIntegrationTest extends LifecycleIntegrationBase {

    @Autowired private DonationCampRepository campRepository;
    @Autowired private DonationSlotRepository slotRepository;
    @Autowired private SlotBookingRepository bookingRepository;
    @Autowired private DeferralRepository deferralRepository;

    private User organiser;
    private User otherOrganiser;
    private User admin;
    private User donorUser;
    private DonorProfile donor;

    @BeforeEach
    void fixtures() {
        organiser = user("organiser", Role.HOSPITAL_REQUESTER, VerificationStatus.VERIFIED);
        otherOrganiser = user("otherorg", Role.BLOOD_BANK_REQUESTER, VerificationStatus.VERIFIED);
        admin = user("admin", Role.ADMIN, VerificationStatus.VERIFIED);
        donorUser = user("donor", Role.DONOR, VerificationStatus.VERIFIED);
        donor = donor(donorUser, "O+", "Colombo");
    }

    private String campJson(String name, String district, LocalDate date, String start, String end, String groups) {
        return "{\"name\":\"" + name + "\",\"district\":\"" + district + "\",\"location\":\"Town Hall\",\"latitude\":7.29,\"longitude\":80.63,"
                + "\"scheduledDate\":\"" + date + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\""
                + (groups == null ? "" : ",\"requiredBloodGroups\":\"" + groups + "\"") + "}";
    }

    private DonationCamp camp(User org, LocalDate date, String status) {
        DonationCamp c = new DonationCamp();
        c.setName("Drive " + date);
        c.setDistrict("Kandy");
        c.setLocation("Town Hall");
        c.setScheduledDate(date);
        c.setStartTime(java.time.LocalTime.of(9, 0));
        c.setEndTime(java.time.LocalTime.of(15, 0));
        c.setOrganizer(org);
        c.setStatus(status);
        return campRepository.save(c);
    }

    private DonationSlot slot(DonationCamp camp, LocalDateTime start, int capacity) {
        DonationSlot s = new DonationSlot();
        s.setCamp(camp);
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(30));
        s.setCapacity(capacity);
        s.setBookedCount(0);
        s.setStatus(SlotStatus.OPEN);
        return slotRepository.save(s);
    }

    private LocalDateTime inDays(int days, int hour) {
        return clock.today().plusDays(days).atTime(hour, 0);
    }

    // ------------------------------------------------------------------ camps

    @Test
    @DisplayName("Creating a camp: past dates, reversed times, unknown districts and blood groups, oversized text are all rejected")
    void createCamp_validation() throws Exception {
        LocalDate future = clock.today().plusDays(10);
        String url = "/api/camps";

        MvcResult ok = mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content(campJson("Kandy Drive", "kandy district", future, "09:00:00", "15:00:00", "O-,a+"))).andExpect(status().isOk()).andReturn();
        assertEquals("Kandy", json(ok).get("district").asText());

        MvcResult past = mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content(campJson("Old", "Kandy", clock.today().minusDays(1), "09:00:00", "15:00:00", null))).andReturn();
        assertEquals(400, past.getResponse().getStatus());
        assertEquals("camp.in_past", json(past).get("code").asText());

        MvcResult reversed = mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content(campJson("Bad", "Kandy", future, "15:00:00", "09:00:00", null))).andReturn();
        assertEquals(400, reversed.getResponse().getStatus());
        assertEquals("time.range_invalid", json(reversed).get("code").asText());
        mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content(campJson("Zero", "Kandy", future, "09:00:00", "09:00:00", null))).andExpect(status().isBadRequest());

        MvcResult district = mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content(campJson("Nowhere", "Atlantis", future, "09:00:00", "15:00:00", null))).andReturn();
        assertEquals("geo.unknown_district", json(district).get("code").asText());

        MvcResult group = mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content(campJson("Groups", "Kandy", future, "09:00:00", "15:00:00", "O-,Z+"))).andReturn();
        assertEquals("blood.group_invalid", json(group).get("code").asText());

        mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content(campJson("N".repeat(256), "Kandy", future, "09:00:00", "15:00:00", null))).andExpect(status().isBadRequest());
        mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content(campJson("Abroad", "Kandy", future, "09:00:00", "15:00:00", null).replace("7.29", "13.08"))).andExpect(status().isBadRequest());
        // donors cannot create camps
        mvc.perform(post(url).with(as(donorUser)).contentType(MediaType.APPLICATION_JSON)
                .content(campJson("Mine", "Kandy", future, "09:00:00", "15:00:00", null))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/camps/{id} works without open-in-view; only the organiser may change it; unknown id is a 404")
    void updateCamp() throws Exception {
        DonationCamp c = camp(organiser, clock.today().plusDays(5), "SCHEDULED");
        String body = campJson("Renamed", "Galle", clock.today().plusDays(5), "08:00:00", "12:00:00", null);

        MvcResult ok = mvc.perform(put("/api/camps/" + c.getId()).with(as(organiser)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        assertEquals("Renamed", json(ok).get("name").asText());
        assertEquals("Galle", json(ok).get("district").asText());

        mvc.perform(put("/api/camps/" + c.getId()).with(as(otherOrganiser)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/camps/999999").with(as(organiser)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        // keeping the old date while renaming is fine even when that date is today; moving it into the past is not
        MvcResult toPast = mvc.perform(put("/api/camps/" + c.getId()).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content(campJson("Renamed", "Galle", clock.today().minusDays(2), "08:00:00", "12:00:00", null))).andReturn();
        assertEquals(400, toPast.getResponse().getStatus());
    }

    @Test
    void upcomingCamps_areDateFiltered_andPublic() throws Exception {
        camp(organiser, clock.today().minusDays(3), "SCHEDULED");    // already happened
        DonationCamp today = camp(organiser, clock.today(), "SCHEDULED");
        DonationCamp later = camp(organiser, clock.today().plusDays(4), "SCHEDULED");
        camp(organiser, clock.today().plusDays(6), "CANCELLED");

        JsonNode camps = json(mvc.perform(get("/api/camps")).andExpect(status().isOk()).andReturn());
        assertEquals(2, camps.size());
        assertEquals(today.getId(), camps.get(0).get("id").asLong());
        assertEquals(later.getId(), camps.get(1).get("id").asLong());
        assertTrue(camps.get(0).get("organizerName").asText().contains("organiser"));

        // recommended camps use the same filter (open to all: no required groups)
        JsonNode rec = json(mvc.perform(get("/api/camps/recommended").with(as(donorUser))).andExpect(status().isOk()).andReturn());
        assertEquals(2, rec.size());
        JsonNode mine = json(mvc.perform(get("/api/camps/my-camps").with(as(organiser))).andExpect(status().isOk()).andReturn());
        assertEquals(4, mine.size());
    }

    @Test
    @DisplayName("Public slot listings work without open-in-view")
    void slotListings_arePublicAndLoadTheirHost() throws Exception {
        DonationCamp c = camp(organiser, clock.today().plusDays(2), "SCHEDULED");
        slot(c, inDays(2, 9), 3);
        DonationSlot hospitalSlot = new DonationSlot();
        hospitalSlot.setHospitalUser(organiser);
        hospitalSlot.setStartTime(inDays(1, 10));
        hospitalSlot.setEndTime(inDays(1, 11));
        hospitalSlot.setCapacity(2);
        hospitalSlot.setBookedCount(0);
        hospitalSlot.setStatus(SlotStatus.OPEN);
        slotRepository.save(hospitalSlot);

        JsonNode camp = data(mvc.perform(get("/api/camps/" + c.getId() + "/slots")).andExpect(status().isOk()).andReturn());
        assertEquals(1, camp.size());
        assertEquals(c.getName(), camp.get(0).get("hostName").asText());
        assertEquals(3, camp.get(0).get("spotsLeft").asInt());

        JsonNode hosp = data(mvc.perform(get("/api/hospitals/" + organiser.getId() + "/slots")).andExpect(status().isOk()).andReturn());
        assertEquals(1, hosp.size());
        assertTrue(hosp.get(0).get("hostName").asText().contains("organiser"));
    }

    // ------------------------------------------------------------------ slots

    @Test
    void createSlots_rejectPastTimesAndReversedRanges() throws Exception {
        DonationCamp c = camp(organiser, clock.today().plusDays(2), "SCHEDULED");
        String url = "/api/camps/" + c.getId() + "/slots";
        LocalDateTime start = inDays(2, 9);

        mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"" + start + "\",\"endTime\":\"" + start.plusMinutes(30) + "\",\"capacity\":3}")).andExpect(status().isOk());

        MvcResult past = mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"" + clock.now().minusHours(2) + "\",\"endTime\":\"" + clock.now().minusHours(1) + "\",\"capacity\":3}")).andReturn();
        assertEquals(400, past.getResponse().getStatus());
        assertEquals("slot.in_past", json(past).get("code").asText());

        mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"" + start + "\",\"endTime\":\"" + start.minusMinutes(5) + "\",\"capacity\":3}")).andExpect(status().isBadRequest());
        mvc.perform(post(url).with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"" + start + "\",\"endTime\":\"" + start + "\",\"capacity\":3}")).andExpect(status().isBadRequest());

        MvcResult bulkPast = mvc.perform(post(url + "/bulk").with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"overallStartTime\":\"" + clock.now().minusHours(3) + "\",\"overallEndTime\":\"" + clock.now().plusHours(3)
                        + "\",\"intervalMinutes\":30,\"capacityPerSlot\":2}")).andReturn();
        assertEquals(400, bulkPast.getResponse().getStatus());
        mvc.perform(post(url + "/bulk").with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"overallStartTime\":\"" + start + "\",\"overallEndTime\":\"" + start.plusHours(2)
                        + "\",\"intervalMinutes\":30,\"capacityPerSlot\":2}")).andExpect(status().isOk());
        // unknown camp: 404, not 500
        mvc.perform(post("/api/camps/999999/slots").with(as(organiser)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"startTime\":\"" + start + "\",\"endTime\":\"" + start.plusMinutes(30) + "\",\"capacity\":3}")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ booking rules

    @Test
    void booking_futureSlotsOnly_unknownIs404_noProfileIs404() throws Exception {
        DonationCamp c = camp(organiser, clock.today().plusDays(3), "SCHEDULED");
        DonationSlot started = slot(c, clock.now().minusMinutes(30), 3);
        DonationSlot future = slot(c, inDays(3, 9), 3);

        MvcResult late = mvc.perform(post("/api/slots/" + started.getId() + "/book").with(as(donorUser))).andReturn();
        assertEquals(409, late.getResponse().getStatus());
        assertEquals("slot.in_past", json(late).get("code").asText());

        mvc.perform(post("/api/slots/" + future.getId() + "/book").with(as(donorUser))).andExpect(status().isOk());
        assertEquals(1, slotRepository.findById(future.getId()).orElseThrow().getBookedCount());
        mvc.perform(post("/api/slots/" + future.getId() + "/book").with(as(donorUser))).andExpect(status().isConflict()); // duplicate

        mvc.perform(post("/api/slots/999999/book").with(as(donorUser))).andExpect(status().isNotFound());
        User noProfile = user("noprofile", Role.DONOR, VerificationStatus.VERIFIED);
        mvc.perform(post("/api/slots/" + future.getId() + "/book").with(as(noProfile))).andExpect(status().isNotFound());
        mvc.perform(get("/api/donors/me/bookings").with(as(noProfile))).andExpect(status().isNotFound());

        JsonNode mine = data(mvc.perform(get("/api/donors/me/bookings").with(as(donorUser))).andExpect(status().isOk()).andReturn());
        assertEquals(1, mine.size());
        mvc.perform(delete("/api/slots/" + future.getId() + "/book").with(as(donorUser))).andExpect(status().isOk());
        assertEquals(0, slotRepository.findById(future.getId()).orElseThrow().getBookedCount());
    }

    @Test
    void booking_overlappingBookingsAreRejected_butAdjacentOnesAreFine() throws Exception {
        DonationCamp c = camp(organiser, clock.today().plusDays(3), "SCHEDULED");
        DonationCamp c2 = camp(otherOrganiser, clock.today().plusDays(3), "SCHEDULED");
        DonationSlot a = slot(c, inDays(3, 9), 3);                                 // 09:00-09:30
        DonationSlot overlapping = slot(c2, inDays(3, 9).plusMinutes(15), 3);      // 09:15-09:45
        DonationSlot adjacent = slot(c2, inDays(3, 9).plusMinutes(30), 3);         // 09:30-10:00

        mvc.perform(post("/api/slots/" + a.getId() + "/book").with(as(donorUser))).andExpect(status().isOk());
        MvcResult clash = mvc.perform(post("/api/slots/" + overlapping.getId() + "/book").with(as(donorUser))).andReturn();
        assertEquals(409, clash.getResponse().getStatus());
        assertEquals("slot.booking_overlap", json(clash).get("code").asText());
        mvc.perform(post("/api/slots/" + adjacent.getId() + "/book").with(as(donorUser))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Eligibility is judged on the day of the slot, not on the day of booking")
    void booking_eligibilityAtTheSlotDate() throws Exception {
        DonorDeferral recent = new DonorDeferral();
        recent.setDonor(donor);
        recent.setReason("RECENT_DONATION");
        recent.setDeferredFrom(clock.today().minusDays(60));
        recent.setDeferredUntil(clock.today().plusDays(10));
        deferralRepository.save(recent);

        DonationCamp c = camp(organiser, clock.today().plusDays(5), "SCHEDULED");
        DonationSlot tooSoon = slot(c, inDays(5, 9), 3);      // still deferred that day
        DonationSlot afterDeferral = slot(c, inDays(20, 9), 3); // eligible again by then

        mvc.perform(post("/api/slots/" + tooSoon.getId() + "/book").with(as(donorUser))).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/slots/" + afterDeferral.getId() + "/book").with(as(donorUser))).andExpect(status().isOk());
        SlotBooking b = bookingRepository.findBySlotIdAndDonorId(afterDeferral.getId(), donor.getId()).orElseThrow();
        assertEquals(afterDeferral.getStartTime().toLocalDate(), b.getEligibleAsOf());

        // a donor who turns 18 only after today but before the slot can book it
        donor.setDateOfBirth(clock.today().minusYears(18).plusDays(10));
        donorRepository.save(donor);
        DonationSlot afterBirthday = slot(c, inDays(30, 9), 3);
        mvc.perform(post("/api/slots/" + tooSoon.getId() + "/book").with(as(donorUser))).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/slots/" + afterBirthday.getId() + "/book").with(as(donorUser))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ organiser roster and check-in

    @Test
    void roster_isForTheOwningOrganiserOnly_andHidesContactDetails() throws Exception {
        DonationCamp c = camp(organiser, clock.today().plusDays(3), "SCHEDULED");
        DonationSlot s = slot(c, inDays(3, 9), 3);
        mvc.perform(post("/api/slots/" + s.getId() + "/book").with(as(donorUser))).andExpect(status().isOk());
        String url = "/api/slots/" + s.getId() + "/bookings";

        for (User viewer : new User[]{organiser, admin}) {
            MvcResult res = mvc.perform(get(url).with(as(viewer))).andExpect(status().isOk()).andReturn();
            JsonNode rows = data(res);
            assertEquals(1, rows.size());
            assertEquals("donor T.", rows.get(0).get("donorName").asText());
            assertEquals("O+", rows.get(0).get("bloodType").asText());
            assertEquals("BOOKED", rows.get(0).get("status").asText());
            assertFalse(res.getResponse().getContentAsString().contains(donorUser.getPhoneNumber()));
            assertFalse(res.getResponse().getContentAsString().contains(donorUser.getEmail()));
        }
        mvc.perform(get(url).with(as(otherOrganiser))).andExpect(status().isForbidden());
        mvc.perform(get(url).with(as(donorUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/slots/999999/bookings").with(as(organiser))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Check-in records the donation through DonationRecorder: history, totals, 4-month deferral")
    void checkIn_recordsTheDonation() throws Exception {
        DonationCamp c = camp(organiser, clock.today().plusDays(1), "SCHEDULED");
        DonationSlot s = slot(c, inDays(1, 9), 3);
        mvc.perform(post("/api/slots/" + s.getId() + "/book").with(as(donorUser))).andExpect(status().isOk());
        SlotBooking booking = bookingRepository.findBySlotIdAndDonorId(s.getId(), donor.getId()).orElseThrow();
        String url = "/api/slots/" + s.getId() + "/check-in/" + booking.getId();

        mvc.perform(post(url).with(as(otherOrganiser))).andExpect(status().isForbidden());
        mvc.perform(post(url).with(as(organiser))).andExpect(status().isOk());

        assertEquals(BookingStatus.CHECKED_IN, bookingRepository.findById(booking.getId()).orElseThrow().getStatus());
        DonorProfile after = donorRepository.findById(donor.getId()).orElseThrow();
        assertEquals(3, after.getTotalDonations());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM donation_records WHERE donor_id = ? AND source = 'SLOT_CHECKIN' AND booking_id = ?",
                Integer.class, donor.getId(), booking.getId()));
        assertEquals(clock.today().plusMonths(4).minusDays(1),
                jdbc.queryForObject("SELECT deferred_until FROM donor_deferrals WHERE donor_id = ?", java.sql.Date.class, donor.getId()).toLocalDate());

        JsonNode e = data(mvc.perform(get("/api/donors/me/eligibility").with(as(donorUser))).andExpect(status().isOk()).andReturn());
        assertFalse(e.get("eligible").asBoolean());
        assertEquals("RECENT_DONATION", e.get("reasons").get(0).asText());
        JsonNode history = data(mvc.perform(get("/api/donors/me/donations").with(as(donorUser))).andExpect(status().isOk()).andReturn());
        assertEquals("SLOT_CHECKIN", history.get(0).get("source").asText());

        // twice is a conflict and does not record a second donation
        mvc.perform(post(url).with(as(organiser))).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM donation_records", Integer.class));
    }
}
