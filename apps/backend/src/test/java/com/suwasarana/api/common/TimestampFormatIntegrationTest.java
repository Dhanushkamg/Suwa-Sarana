package com.suwasarana.api.common;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.testsupport.LifecycleIntegrationBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Contract: a MOMENT (created / expires / closed ...) is an ISO-8601 instant with a trailing Z; only wall-clock values
 * that people read in Sri Lanka (slot and camp start / end times, camp dates) are zone-less. A zone-less moment would be
 * read as local time by a browser and be 5.5 hours off.
 */
class TimestampFormatIntegrationTest extends LifecycleIntegrationBase {

    @Test
    @DisplayName("request and profile moments carry a Z; slot times stay zone-less wall-clock")
    void momentsAreInstants() throws Exception {
        User owner = user("owner", Role.REQUESTER, VerificationStatus.VERIFIED);
        BloodRequest r = request(owner, RequestStatus.OPEN, "O+", 2, "Colombo");

        MvcResult one = mvc.perform(get("/api/requests/" + r.getId()).with(as(owner))).andReturn();
        assertTrue(data(one).get("createdAt").asText().endsWith("Z"), data(one).get("createdAt").asText());
        assertTrue(data(one).get("expiresAt").asText().endsWith("Z"), data(one).get("expiresAt").asText());

        MvcResult me = mvc.perform(get("/api/users/me").with(as(owner))).andReturn();
        assertTrue(data(me).get("createdAt").asText().endsWith("Z"), data(me).get("createdAt").asText());
    }
}
