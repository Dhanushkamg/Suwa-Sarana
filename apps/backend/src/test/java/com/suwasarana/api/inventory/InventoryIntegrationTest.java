package com.suwasarana.api.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.suwasarana.api.inventory.InventoryModel.BatchStatus;
import com.suwasarana.api.inventory.InventoryModel.Component;
import com.suwasarana.api.inventory.InventoryModel.Unit;
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

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryIntegrationTest extends LifecycleIntegrationBase {

    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryUnitRepository unitRepository;
    @Autowired private InventorySiteRepository siteRepository;

    private User bank;
    private User otherBank;
    private User pendingBank;
    private User donorUser;

    private static final String BASE = "/api/blood-bank";

    @BeforeEach
    void fixtures() {
        bank = user("nationalbank", Role.BLOOD_BANK_REQUESTER, VerificationStatus.VERIFIED);
        otherBank = user("otherbank", Role.HOSPITAL_REQUESTER, VerificationStatus.VERIFIED);
        pendingBank = user("pendingbank", Role.BLOOD_BANK_REQUESTER, VerificationStatus.PENDING);
        donorUser = user("donor", Role.DONOR, VerificationStatus.VERIFIED);
    }

    private String collection(String type, String component, int units, LocalDate expires) {
        return "{\"bloodType\":\"" + type + "\",\"component\":\"" + component + "\",\"units\":" + units + ",\"expiresOn\":\"" + expires + "\"}";
    }

    private JsonNode record(User as, String type, int units, LocalDate expires) throws Exception {
        MvcResult r = mvc.perform(post(BASE + "/stock").with(as(as)).contentType(MediaType.APPLICATION_JSON)
                .content(collection(type, "WHOLE_BLOOD", units, expires))).andExpect(status().isOk()).andReturn();
        return data(r);
    }

    private void threshold(User as, String type, int low, int critical) throws Exception {
        mvc.perform(put(BASE + "/thresholds").with(as(as)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bloodType\":\"" + type + "\",\"low\":" + low + ",\"critical\":" + critical + "}")).andExpect(status().isOk());
    }

    private void issue(User as, String type, int units) throws Exception {
        mvc.perform(post(BASE + "/stock/issue").with(as(as)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bloodType\":\"" + type + "\",\"component\":\"WHOLE_BLOOD\",\"units\":" + units + "}")).andExpect(status().isOk());
    }

    private JsonNode line(User as, String type, String component) throws Exception {
        JsonNode lines = data(mvc.perform(get(BASE + "/stock").with(as(as))).andExpect(status().isOk()).andReturn()).get("lines");
        for (JsonNode l : lines) if (l.get("bloodType").asText().equals(type) && l.get("component").asText().equals(component)) return l;
        return null;
    }

    private long alerts(User u) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'LOW_STOCK'", Long.class, u.getId());
    }

    // ------------------------------------------------------------------ access

    @Test
    void onlyVerifiedInstitutionsManageStock() throws Exception {
        MvcResult pending = mvc.perform(get(BASE + "/stock").with(as(pendingBank))).andReturn();
        assertEquals(403, pending.getResponse().getStatus());
        assertEquals("verification.required", json(pending).get("code").asText());
        mvc.perform(post(BASE + "/stock").with(as(pendingBank)).contentType(MediaType.APPLICATION_JSON)
                .content(collection("O-", "WHOLE_BLOOD", 5, LocalDate.now().plusDays(20)))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/stock").with(as(donorUser))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/stock")).andExpect(status().isUnauthorized());
        assertEquals(0, unitRepository.count());
    }

    @Test
    void validation() throws Exception {
        LocalDate in20 = clock.today().plusDays(20);
        mvc.perform(post(BASE + "/stock").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bloodType\":\"O-\",\"component\":\"WHOLE_BLOOD\",\"units\":5}")).andExpect(status().isBadRequest());   // no expiry
        mvc.perform(post(BASE + "/stock").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content(collection("O-", "WHOLE_BLOOD", 0, in20))).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/stock").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content(collection("Z+", "WHOLE_BLOOD", 5, in20))).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/stock").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content(collection("O-", "BEER", 5, in20))).andExpect(status().isBadRequest());
        MvcResult expired = mvc.perform(post(BASE + "/stock").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content(collection("O-", "WHOLE_BLOOD", 5, clock.today().minusDays(1)))).andReturn();
        assertEquals(400, expired.getResponse().getStatus());
        assertEquals("inventory.date_invalid", json(expired).get("code").asText());
        MvcResult bad = mvc.perform(put(BASE + "/thresholds").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bloodType\":\"O-\",\"low\":3,\"critical\":8}")).andReturn();
        assertEquals("inventory.threshold_invalid", json(bad).get("code").asText());
        MvcResult district = mvc.perform(put(BASE + "/site").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"district\":\"Atlantis\"}")).andReturn();
        assertEquals("geo.unknown_district", json(district).get("code").asText());
    }

    // ------------------------------------------------------------------ status and alerts

    @Test
    @DisplayName("Status is computed from units and the institution's own thresholds; the owner is alerted once per state")
    void statusAndLowStockAlerts() throws Exception {
        threshold(bank, "O-", 10, 4);
        record(bank, "O-", 20, clock.today().plusDays(30));
        assertEquals("OK", line(bank, "O-", "WHOLE_BLOOD").get("status").asText());
        assertEquals(0, alerts(bank));

        issue(bank, "O-", 8);                                        // 12 left
        assertEquals("OK", line(bank, "O-", "WHOLE_BLOOD").get("status").asText());
        issue(bank, "O-", 3);                                        // 9 left: at or below low
        JsonNode low = line(bank, "O-", "WHOLE_BLOOD");
        assertEquals("LOW", low.get("status").asText());
        assertEquals(9, low.get("units").asInt());
        assertEquals(10, low.get("lowThreshold").asInt());
        assertEquals(1, alerts(bank));

        issue(bank, "O-", 1);                                        // 8 left: still LOW, no second alert
        assertEquals(1, alerts(bank));
        issue(bank, "O-", 4);                                        // 4 left: CRITICAL
        assertEquals("CRITICAL", line(bank, "O-", "WHOLE_BLOOD").get("status").asText());
        assertEquals(2, alerts(bank));
        issue(bank, "O-", 1);                                        // still CRITICAL
        assertEquals(2, alerts(bank));

        record(bank, "O-", 30, clock.today().plusDays(30));          // recovered
        assertEquals("OK", line(bank, "O-", "WHOLE_BLOOD").get("status").asText());
        issue(bank, "O-", 25);                                       // 8 left: LOW again, alerted again
        assertEquals("LOW", line(bank, "O-", "WHOLE_BLOOD").get("status").asText());
        assertEquals(3, alerts(bank));

        // the alert carries translation keys and parameters, not a sentence
        JsonNode notification = json(mvc.perform(get("/api/notifications").with(as(bank))).andExpect(status().isOk()).andReturn()).get("data").get("items").get(0);
        assertEquals("LOW_STOCK", notification.get("type").asText());
        assertEquals("notifications.lowStock.title", notification.get("titleKey").asText());
        assertEquals("O-", notification.get("params").get("bloodType").asText());
        assertEquals("LOW", notification.get("params").get("status").asText());
        // and nobody else got it
        assertEquals(0, alerts(otherBank));
    }

    @Test
    void expiringSoonAndExpiredUnitsAreNotCountedAsUsable() throws Exception {
        record(bank, "A+", 6, clock.today().plusDays(2));      // expires soon (default window 3 days)
        record(bank, "A+", 10, clock.today().plusDays(30));
        JsonNode l = line(bank, "A+", "WHOLE_BLOOD");
        assertEquals("EXPIRING_SOON", l.get("status").asText());
        assertEquals(16, l.get("units").asInt());
        assertEquals(6, l.get("expiringSoonUnits").asInt());
        assertEquals(clock.today().plusDays(2).toString(), l.get("nextExpiry").asText());

        // a batch whose date has passed but the nightly job has not run yet is already ignored
        Unit stale = new Unit();
        stale.setOwnerUserId(bank.getId());
        stale.setBloodType("B+");
        stale.setComponent(Component.WHOLE_BLOOD);
        stale.setUnits(50);
        stale.setCollectedOn(clock.today().minusDays(40));
        stale.setExpiresOn(clock.today().minusDays(1));
        unitRepository.save(stale);
        threshold(bank, "B+", 10, 4);
        JsonNode b = line(bank, "B+", "WHOLE_BLOOD");
        assertEquals(0, b.get("units").asInt());
        assertEquals("CRITICAL", b.get("status").asText());

        // the expiry job then marks it, logs the movement and tells the owner
        assertEquals(1, inventoryService.expireBatches());
        assertEquals(BatchStatus.EXPIRED, unitRepository.findById(stale.getId()).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE reason = 'EXPIRY' AND delta = -50 AND owner_user_id = ?", Integer.class, bank.getId()));
        assertEquals(0, inventoryService.expireBatches(), "idempotent");
    }

    @Test
    @DisplayName("Issuing takes the earliest-expiring units first and is all-or-nothing")
    void issue_isFirstExpiryFirstOut_andAtomic() throws Exception {
        JsonNode later = record(bank, "B-", 5, clock.today().plusDays(30));
        JsonNode sooner = record(bank, "B-", 5, clock.today().plusDays(10));

        issue(bank, "B-", 7);
        assertEquals(0, unitRepository.findById(sooner.get("id").asLong()).orElseThrow().getUnits());
        assertEquals(BatchStatus.DEPLETED, unitRepository.findById(sooner.get("id").asLong()).orElseThrow().getStatus());
        assertEquals(3, unitRepository.findById(later.get("id").asLong()).orElseThrow().getUnits());

        MvcResult tooMany = mvc.perform(post(BASE + "/stock/issue").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bloodType\":\"B-\",\"component\":\"WHOLE_BLOOD\",\"units\":4}")).andReturn();
        assertEquals(409, tooMany.getResponse().getStatus());
        assertEquals("inventory.insufficient_stock", json(tooMany).get("code").asText());
        assertEquals(3, unitRepository.findById(later.get("id").asLong()).orElseThrow().getUnits(), "nothing was taken");

        // the movement log is the audit trail
        JsonNode moves = data(mvc.perform(get(BASE + "/stock/movements").with(as(bank))).andExpect(status().isOk()).andReturn());
        assertEquals(4, moves.size(), "2 collections + 2 issues");
        assertEquals("ISSUE", moves.get(0).get("reason").asText());
        int net = 0;
        for (JsonNode m : moves) net += m.get("delta").asInt();
        assertEquals(3, net);
    }

    @Test
    void batches_canBeCorrectedAndRemoved_butOnlyByTheirOwner() throws Exception {
        JsonNode batch = record(bank, "AB+", 10, clock.today().plusDays(25));
        long id = batch.get("id").asLong();

        mvc.perform(put(BASE + "/stock/" + id).with(as(otherBank)).contentType(MediaType.APPLICATION_JSON).content("{\"units\":99}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/stock/" + id).with(as(otherBank))).andExpect(status().isNotFound());

        mvc.perform(put(BASE + "/stock/" + id).with(as(bank)).contentType(MediaType.APPLICATION_JSON).content("{\"units\":7,\"note\":\"recount\"}"))
                .andExpect(status().isOk());
        assertEquals(7, unitRepository.findById(id).orElseThrow().getUnits());
        assertEquals(-3, jdbc.queryForObject("SELECT delta FROM inventory_movements WHERE reason = 'ADJUSTMENT'", Integer.class));
        mvc.perform(put(BASE + "/stock/" + id).with(as(bank)).contentType(MediaType.APPLICATION_JSON).content("{\"units\":-1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete(BASE + "/stock/" + id).with(as(bank))).andExpect(status().isOk());
        assertFalse(unitRepository.findById(id).isPresent());
        assertEquals(-7, jdbc.queryForObject("SELECT delta FROM inventory_movements WHERE reason = 'DISCARD'", Integer.class));
        assertNull(line(bank, "AB+", "WHOLE_BLOOD"));
    }

    // ------------------------------------------------------------------ the public ticker

    @Test
    @DisplayName("The public ticker is derived from real stock: same JSON fields, LOW/CRITICAL only, verified and fresh institutions only")
    void publicSignals() throws Exception {
        threshold(bank, "O-", 10, 4);
        threshold(bank, "A+", 10, 4);
        record(bank, "O-", 3, clock.today().plusDays(20));      // CRITICAL
        record(bank, "A+", 9, clock.today().plusDays(20));      // LOW
        record(bank, "B+", 50, clock.today().plusDays(20));     // plenty
        threshold(bank, "AB-", 10, 4);                          // threshold only, no stock ever recorded: unknown, not a shortage
        mvc.perform(post(BASE + "/stock").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content(collection("O+", "PLATELETS", 1, clock.today().plusDays(4)))).andExpect(status().isOk());
        mvc.perform(put(BASE + "/thresholds").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bloodType\":\"O+\",\"component\":\"PLATELETS\",\"low\":10,\"critical\":4}")).andExpect(status().isOk());

        // no district yet: the institution is not published
        assertEquals(0, json(mvc.perform(get("/api/inventory/stock-signals")).andExpect(status().isOk()).andReturn()).size());

        mvc.perform(put(BASE + "/site").with(as(bank)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"siteName\":\"National Blood Centre\",\"district\":\"colombo\",\"latitude\":6.92,\"longitude\":79.86}")).andExpect(status().isOk());

        JsonNode signals = json(mvc.perform(get("/api/inventory/stock-signals")).andExpect(status().isOk()).andReturn());
        assertTrue(signals.isArray(), "plain array, as the landing-page ticker reads it");
        assertEquals(2, signals.size(), "O- critical, A+ low; AB- was never recorded, platelets are not part of the ticker, B+ is fine");
        assertEquals("CRITICAL", signals.get(0).get("status").asText(), "critical first");
        for (JsonNode s : signals) {
            assertEquals("National Blood Centre", s.get("hospitalName").asText());
            assertEquals("Colombo", s.get("district").asText());
            assertTrue(s.has("bloodType") && s.has("status") && s.has("updatedAt"));
            assertNotNull(s.get("updatedAt").asText());
            assertTrue(List.of("LOW", "CRITICAL").contains(s.get("status").asText()));
        }
        assertTrue(signals.toString().contains("\"O-\"") && signals.toString().contains("\"A+\"") && !signals.toString().contains("\"B+\""));

        // switched off by the institution
        mvc.perform(put(BASE + "/site").with(as(bank)).contentType(MediaType.APPLICATION_JSON).content("{\"publicSignals\":false}")).andExpect(status().isOk());
        assertEquals(0, inventoryService.publicSignals().size());
        mvc.perform(put(BASE + "/site").with(as(bank)).contentType(MediaType.APPLICATION_JSON).content("{\"publicSignals\":true}")).andExpect(status().isOk());
        assertEquals(2, inventoryService.publicSignals().size());

        // confirmed again after 40 h keeps it fresh; untouched for 49 h it disappears (stale shortages are not claimed)
        java.time.Instant t0 = clock.instant();
        clock.setClock(Clock.fixed(t0.plus(Duration.ofHours(40)), clock.zone()));
        assertEquals(2, inventoryService.publicSignals().size(), "40 h old is still fresh");
        clock.setClock(Clock.fixed(t0.plus(Duration.ofHours(49)), clock.zone()));
        assertEquals(0, inventoryService.publicSignals().size(), "older than 48 h is hidden");
        mvc.perform(post(BASE + "/stock/confirm").with(as(bank))).andExpect(status().isOk());
        assertEquals(2, inventoryService.publicSignals().size(), "confirming stock makes it fresh again");
        clock.setClock(null);

        // an institution that loses its verification is no longer published
        bank.setVerificationStatus(VerificationStatus.REJECTED);
        userRepository.save(bank);
        assertEquals(0, inventoryService.publicSignals().size());
    }

    @Test
    void siteProfile_defaultsToTheAccountName() throws Exception {
        JsonNode site = data(mvc.perform(get(BASE + "/site").with(as(bank))).andExpect(status().isOk()).andReturn());
        assertEquals("nationalbank Tester", site.get("siteName").asText());
        assertTrue(site.get("district").isNull());
        assertTrue(site.get("publicSignals").asBoolean());
        assertEquals(1, siteRepository.count());
    }
}
