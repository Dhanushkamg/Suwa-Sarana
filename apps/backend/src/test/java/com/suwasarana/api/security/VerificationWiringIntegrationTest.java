package com.suwasarana.api.security;

import com.suwasarana.api.camp.DonationCamp;
import com.suwasarana.api.camp.DonationCampRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The VerificationGuard seam: camps, slots, check-in, URGENT/CRITICAL requests and stock writes are refused for
 * institutions that are not VERIFIED with a confirmed email, and for REJECTED accounts, with the guard's codes.
 */
class VerificationWiringIntegrationTest extends LifecycleIntegrationBase {

    @Autowired private DonationCampRepository campRepository;
    @Autowired private DonationSlotRepository slotRepository;
    @Autowired private SlotBookingRepository bookingRepository;

    private User institution(String name, Role role, VerificationStatus status, boolean emailConfirmed) {
        User u = user(name, role, status);
        u.setEmailVerifiedAt(emailConfirmed ? Instant.now() : null);
        return userRepository.save(u);
    }

    private String campJson() {
        return "{\"name\":\"Drive\",\"district\":\"Kandy\",\"location\":\"Town Hall\",\"latitude\":7.29,\"longitude\":80.63,"
                + "\"scheduledDate\":\"" + clock.today().plusDays(10) + "\",\"startTime\":\"09:00:00\",\"endTime\":\"15:00:00\"}";
    }

    private String requestJson(String urgency) {
        return "{\"patientBloodType\":\"O+\",\"unitsNeeded\":2,\"urgency\":\"" + urgency + "\",\"hospitalName\":\"Base Hospital\","
                + "\"district\":\"Kandy\",\"latitude\":7.29,\"longitude\":80.63}";
    }

    private void assertCode(MvcResult result, int status, String code) throws Exception {
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals(code, json(result).get("code").asText());
    }

    @Test
    @DisplayName("an unverified or unconfirmed institution cannot publish camps; a verified one can")
    void camps() throws Exception {
        User pending = institution("pending", Role.HOSPITAL_REQUESTER, VerificationStatus.PENDING, true);
        User noEmail = institution("noemail", Role.BLOOD_BANK_REQUESTER, VerificationStatus.VERIFIED, false);
        User rejected = institution("rejected", Role.HOSPITAL_REQUESTER, VerificationStatus.REJECTED, true);
        User verified = institution("verified", Role.HOSPITAL_REQUESTER, VerificationStatus.VERIFIED, true);

        assertCode(mvc.perform(post("/api/camps").with(as(pending)).contentType(MediaType.APPLICATION_JSON).content(campJson())).andReturn(), 403, "verification.required");
        assertCode(mvc.perform(post("/api/camps").with(as(noEmail)).contentType(MediaType.APPLICATION_JSON).content(campJson())).andReturn(), 403, "email.verificationRequired");
        assertCode(mvc.perform(post("/api/camps").with(as(rejected)).contentType(MediaType.APPLICATION_JSON).content(campJson())).andReturn(), 403, "verification.rejected");
        assertEquals(200, mvc.perform(post("/api/camps").with(as(verified)).contentType(MediaType.APPLICATION_JSON).content(campJson())).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("editing or cancelling a camp, creating slots and checking donors in need a verified organisation")
    void campEditsSlotsAndCheckIn() throws Exception {
        User org = institution("org", Role.HOSPITAL_REQUESTER, VerificationStatus.VERIFIED, true);
        DonationCamp camp = new DonationCamp();
        camp.setName("Drive");
        camp.setDistrict("Kandy");
        camp.setLocation("Town Hall");
        camp.setScheduledDate(clock.today().plusDays(5));
        camp.setStartTime(LocalTime.of(9, 0));
        camp.setEndTime(LocalTime.of(15, 0));
        camp.setOrganizer(org);
        camp.setStatus("SCHEDULED");
        camp = campRepository.save(camp);

        DonationSlot slot = new DonationSlot();
        slot.setCamp(camp);
        slot.setStartTime(clock.today().plusDays(5).atTime(10, 0));
        slot.setEndTime(clock.today().plusDays(5).atTime(10, 30));
        slot.setCapacity(5);
        slot.setBookedCount(1);
        slot.setStatus(SlotStatus.OPEN);
        slot = slotRepository.save(slot);
        User donorUser = user("donor", Role.DONOR, VerificationStatus.VERIFIED);
        DonorProfile donor = donor(donorUser, "O+", "Kandy");
        SlotBooking booking = new SlotBooking();
        booking.setSlot(slot);
        booking.setDonor(donor);
        booking.setStatus(BookingStatus.BOOKED);
        booking = bookingRepository.save(booking);

        // the organisation is rejected after publishing: everything it could do is now refused
        org.setVerificationStatus(VerificationStatus.REJECTED);
        userRepository.save(org);

        assertCode(mvc.perform(put("/api/camps/" + camp.getId()).with(as(org)).contentType(MediaType.APPLICATION_JSON).content(campJson())).andReturn(), 403, "verification.rejected");
        assertCode(mvc.perform(patch("/api/camps/" + camp.getId() + "/status").with(as(org)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}")).andReturn(), 403, "verification.rejected");
        String slotJson = "{\"startTime\":\"" + clock.today().plusDays(6).atTime(10, 0) + "\",\"endTime\":\"" + clock.today().plusDays(6).atTime(11, 0) + "\",\"capacity\":3}";
        assertCode(mvc.perform(post("/api/camps/" + camp.getId() + "/slots").with(as(org)).contentType(MediaType.APPLICATION_JSON).content(slotJson)).andReturn(), 403, "verification.rejected");
        assertCode(mvc.perform(post("/api/hospitals/" + org.getId() + "/slots").with(as(org)).contentType(MediaType.APPLICATION_JSON).content(slotJson)).andReturn(), 403, "verification.rejected");
        assertCode(mvc.perform(post("/api/slots/" + slot.getId() + "/check-in/" + booking.getId()).with(as(org))).andReturn(), 403, "verification.rejected");

        // an administrator cannot publish slots on behalf of an organisation that is not verified either
        User admin = user("admin", Role.ADMIN, VerificationStatus.VERIFIED);
        assertCode(mvc.perform(post("/api/hospitals/" + org.getId() + "/slots").with(as(admin)).contentType(MediaType.APPLICATION_JSON).content(slotJson)).andReturn(), 403, "verification.rejected");
    }

    @Test
    @DisplayName("URGENT and CRITICAL requests need a verified institution; REJECTED accounts cannot raise any request")
    void requests() throws Exception {
        User pendingHospital = institution("pendinghospital", Role.HOSPITAL_REQUESTER, VerificationStatus.PENDING, true);
        User pendingRequester = institution("pendingrequester", Role.REQUESTER, VerificationStatus.PENDING, true);
        User rejected = institution("rejectedrequester", Role.REQUESTER, VerificationStatus.REJECTED, true);

        assertCode(mvc.perform(post("/api/requests").with(as(pendingHospital)).contentType(MediaType.APPLICATION_JSON).content(requestJson("URGENT"))).andReturn(), 403, "verification.required");
        assertCode(mvc.perform(post("/api/requests").with(as(pendingRequester)).contentType(MediaType.APPLICATION_JSON).content(requestJson("CRITICAL"))).andReturn(), 403, "request.requester_not_verified");
        assertCode(mvc.perform(post("/api/requests").with(as(rejected)).contentType(MediaType.APPLICATION_JSON).content(requestJson("ROUTINE"))).andReturn(), 403, "verification.rejected");
        // ROUTINE and URGENT from a plain requester are fine
        assertEquals(200, mvc.perform(post("/api/requests").with(as(pendingRequester)).contentType(MediaType.APPLICATION_JSON).content(requestJson("ROUTINE"))).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("stock writes need a verified institution with a confirmed email")
    void stock() throws Exception {
        User pending = institution("stockpending", Role.BLOOD_BANK_REQUESTER, VerificationStatus.PENDING, true);
        User noEmail = institution("stocknoemail", Role.BLOOD_BANK_REQUESTER, VerificationStatus.VERIFIED, false);
        String body = "{\"bloodType\":\"O+\",\"component\":\"WHOLE_BLOOD\",\"units\":5,\"expiresOn\":\"" + LocalDate.now().plusDays(20) + "\"}";
        assertCode(mvc.perform(post("/api/blood-bank/stock").with(as(pending)).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn(), 403, "verification.required");
        assertCode(mvc.perform(post("/api/blood-bank/stock").with(as(noEmail)).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn(), 403, "email.verificationRequired");
    }
}
