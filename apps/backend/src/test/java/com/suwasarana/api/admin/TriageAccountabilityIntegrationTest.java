package com.suwasarana.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.suwasarana.api.ai.AiTriageRecord;
import com.suwasarana.api.ai.AiTriageRecordRepository;
import com.suwasarana.api.ai.RequestTriageAiService;
import com.suwasarana.api.audit.AdminAuditLog;
import com.suwasarana.api.audit.AdminAuditLogRepository;
import com.suwasarana.api.audit.AuditAction;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.testsupport.LifecycleIntegrationBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-19 (AI half): a triage review keeps the AI score, records who decided what, shows the reasons, and is audited. */
class TriageAccountabilityIntegrationTest extends LifecycleIntegrationBase {

    @Autowired private RequestTriageAiService triage;
    @Autowired private AiTriageRecordRepository records;
    @Autowired private AdminAuditLogRepository auditLog;

    private static List<String> codes(JsonNode item) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : item.path("reasons")) out.add(n.path("code").asText());
        return out;
    }

    @Test
    @DisplayName("review: AI score stays, reviewer/decision/note are stored, queue shows reasons and the review, audit row written")
    void reviewKeepsTheScoreAndIsAudited() throws Exception {
        User admin = user("triageadmin", Role.ADMIN, VerificationStatus.VERIFIED);
        User requester = user("triagereq", Role.REQUESTER, VerificationStatus.PENDING);
        BloodRequest r = request(requester, RequestStatus.OPEN, "O+", 8, "Galle");
        r.setHospitalName("test");
        r = triage.triageRequest(r); // saves; the entity keeps its requester (a re-loaded one would be a lazy proxy outside a session)
        int aiScore = r.getFraudRiskScore();
        assertTrue(aiScore >= 40, "8 units, unverified, suspicious name: " + aiScore);

        mvc.perform(put("/api/admin/triage/" + r.getId() + "/review").with(as(admin)).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"called the hospital\",\"decision\":\"dismissed\"}"))
                .andExpect(status().isOk());

        // the AI score is untouched
        BloodRequest after = requestRepository.findById(r.getId()).orElseThrow();
        assertEquals(aiScore, after.getFraudRiskScore());
        assertNotEquals(0, after.getFraudRiskScore());

        // who / when / what
        AiTriageRecord rec = records.findFirstByRequestIdOrderByCreatedAtDescIdDesc(r.getId()).orElseThrow();
        assertEquals(admin.getId(), rec.getReviewedBy());
        assertNotNull(rec.getReviewedAt());
        assertEquals("DISMISSED", rec.getReviewDecision());
        assertEquals("called the hospital", rec.getReviewNote());
        assertEquals(aiScore, rec.getScore());

        // the queue exposes the AI reasons (code + weight) and the review
        JsonNode queue = json(mvc.perform(get("/api/admin/triage/queue").with(as(admin)).with(fromNewClient()))
                .andExpect(status().isOk()).andReturn()).path("data");
        JsonNode item = null;
        for (JsonNode n : queue) if (n.path("id").asLong() == r.getId()) item = n;
        assertNotNull(item);
        assertEquals(aiScore, item.path("fraudRiskScore").asInt());
        assertTrue(codes(item).contains("SUSPICIOUS_HOSPITAL_NAME"), codes(item).toString());
        assertTrue(item.path("reasons").get(0).has("weight"));
        assertTrue(item.path("reviewed").asBoolean());
        assertEquals("DISMISSED", item.path("reviewDecision").asText());
        assertEquals(admin.getId(), item.path("reviewedBy").asLong());

        // the ADMIN audit trail
        List<AdminAuditLog> rows = auditLog.findAll().stream().filter(a -> AuditAction.TRIAGE_REVIEWED.name().equals(a.getAction())).toList();
        assertEquals(1, rows.size());
        assertEquals(admin.getId(), rows.get(0).getActorId());
        assertEquals("ADMIN", rows.get(0).getActorRole());
        assertTrue(rows.get(0).getDetails().contains("requestId=" + r.getId()));
        assertTrue(rows.get(0).getDetails().contains("decision=DISMISSED"));
    }

    @Test
    @DisplayName("a reviewed request moves behind the pending ones and a bad decision is a 400")
    void reviewedItemsSinkAndDecisionIsValidated() throws Exception {
        User admin = user("triageadmin2", Role.ADMIN, VerificationStatus.VERIFIED);
        User requester = user("triagereq2", Role.REQUESTER, VerificationStatus.PENDING);
        BloodRequest hi = request(requester, RequestStatus.OPEN, "O+", 8, "Galle");
        hi.setHospitalName("test");
        hi = triage.triageRequest(hi);
        BloodRequest lo = request(requester, RequestStatus.OPEN, "A+", 1, "Kandy");
        lo = triage.triageRequest(lo);
        assertTrue(hi.getFraudRiskScore() > lo.getFraudRiskScore());

        mvc.perform(put("/api/admin/triage/" + hi.getId() + "/review").with(as(admin)).with(fromNewClient())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"MAYBE\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/triage/" + hi.getId() + "/review").with(as(admin)).with(fromNewClient())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        JsonNode queue = json(mvc.perform(get("/api/admin/triage/queue").with(as(admin)).with(fromNewClient())).andReturn()).path("data");
        assertEquals(lo.getId(), queue.get(0).path("id").asLong(), "the pending request comes first");
        assertEquals(hi.getId(), queue.get(1).path("id").asLong());
        assertEquals("REVIEWED", queue.get(1).path("reviewDecision").asText());
    }

    @Test
    @DisplayName("unknown request id is 404")
    void unknownRequest() throws Exception {
        User admin = user("triageadmin3", Role.ADMIN, VerificationStatus.VERIFIED);
        mvc.perform(put("/api/admin/triage/987654/review").with(as(admin)).with(fromNewClient())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }
}
