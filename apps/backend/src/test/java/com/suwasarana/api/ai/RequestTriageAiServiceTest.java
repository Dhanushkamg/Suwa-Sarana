package com.suwasarana.api.ai;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RequestTriageAiServiceTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private AiTriageRecordRepository recordRepository;

    @InjectMocks
    private RequestTriageAiService triageService;

    private User unverifiedUser;
    private User verifiedUser;

    @BeforeEach
    void setUp() {
        unverifiedUser = new User();
        unverifiedUser.setId(10L);
        unverifiedUser.setEmail("user10@test.com");
        unverifiedUser.setRole(Role.REQUESTER);
        unverifiedUser.setVerificationStatus(VerificationStatus.PENDING);

        verifiedUser = new User();
        verifiedUser.setId(20L);
        verifiedUser.setEmail("hospital@test.com");
        verifiedUser.setRole(Role.HOSPITAL_REQUESTER);
        verifiedUser.setVerificationStatus(VerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("Should assign elevated risk score to rapid duplicate requests from same requester")
    void testTriage_rapidDuplicatesGetHighScore() {
        BloodRequest req1 = new BloodRequest();
        req1.setId(101L);
        req1.setCreatedAt(LocalDateTime.now().minusHours(2));

        BloodRequest req2 = new BloodRequest();
        req2.setId(102L);
        req2.setCreatedAt(LocalDateTime.now().minusHours(1));

        BloodRequest currentReq = new BloodRequest();
        currentReq.setId(103L);
        currentReq.setRequester(unverifiedUser);
        currentReq.setHospitalName("National Hospital Colombo");
        currentReq.setUnitsNeeded((short) 1);
        currentReq.setUrgency(Urgency.URGENT);
        currentReq.setStatus(RequestStatus.OPEN);

        when(requestRepository.findByRequesterId(10L)).thenReturn(Arrays.asList(req1, req2, currentReq));
        when(requestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        BloodRequest result = triageService.triageRequest(currentReq);

        assertNotNull(result);
        assertTrue(result.getFraudRiskScore() >= 45);
        assertTrue(result.getAiFlagReason().contains("High frequency"));
        assertNotNull(result.getTriagedAt());
    }

    @Test
    @DisplayName("Should assign elevated risk score to abnormal unit volumes by unverified users")
    void testTriage_highVolumeUnverifiedGetsScore() {
        BloodRequest currentReq = new BloodRequest();
        currentReq.setId(201L);
        currentReq.setRequester(unverifiedUser);
        currentReq.setHospitalName("Colombo General Hospital");
        currentReq.setUnitsNeeded((short) 8); // High volume
        currentReq.setUrgency(Urgency.URGENT);
        currentReq.setStatus(RequestStatus.OPEN);

        when(requestRepository.findByRequesterId(10L)).thenReturn(Collections.singletonList(currentReq));
        when(requestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        BloodRequest result = triageService.triageRequest(currentReq);

        assertNotNull(result);
        assertTrue(result.getFraudRiskScore() >= 35);
        assertTrue(result.getAiFlagReason().contains("High unit volume"));
    }

    @Test
    @DisplayName("Should NEVER mutate request lifecycle status during triage")
    void testTriage_statusNeverAlteredByTriage() {
        BloodRequest currentReq = new BloodRequest();
        currentReq.setId(301L);
        currentReq.setRequester(unverifiedUser);
        currentReq.setHospitalName("test"); // Suspicious
        currentReq.setUnitsNeeded((short) 9); // Suspicious
        currentReq.setUrgency(Urgency.CRITICAL);
        currentReq.setStatus(RequestStatus.OPEN);

        when(requestRepository.findByRequesterId(10L)).thenReturn(Collections.emptyList());
        when(requestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        BloodRequest result = triageService.triageRequest(currentReq);

        assertNotNull(result);
        assertEquals(RequestStatus.OPEN, result.getStatus(), "AI triage must NEVER mutate status to CANCELLED or REJECTED");
        assertTrue(result.getFraudRiskScore() > 50);
    }

    @Test
    @DisplayName("Should assign low risk score to standard verified hospital requests")
    void testTriage_standardVerifiedRequestHasLowScore() {
        BloodRequest currentReq = new BloodRequest();
        currentReq.setId(401L);
        currentReq.setRequester(verifiedUser);
        currentReq.setHospitalName("National Hospital of Sri Lanka");
        currentReq.setUnitsNeeded((short) 2);
        currentReq.setUrgency(Urgency.URGENT);
        currentReq.setStatus(RequestStatus.OPEN);

        when(requestRepository.findByRequesterId(20L)).thenReturn(Collections.singletonList(currentReq));
        when(requestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        BloodRequest result = triageService.triageRequest(currentReq);

        assertNotNull(result);
        assertEquals(0, result.getFraudRiskScore());
        assertTrue(result.getAiFlagReason().contains("Standard requisition profile"));
    }

    @Test
    @DisplayName("TriageResult lists the reasons heaviest first with stable codes, and the stored record keeps them")
    void testTriage_reasonsAreOrderedAndStored() {
        BloodRequest req = new BloodRequest();
        req.setId(501L);
        req.setRequester(unverifiedUser);
        req.setHospitalName("test");
        req.setUnitsNeeded((short) 8);
        req.setUrgency(Urgency.CRITICAL);
        req.setStatus(RequestStatus.OPEN);
        when(requestRepository.findByRequesterId(10L)).thenReturn(Collections.emptyList());
        when(requestRepository.save(any(BloodRequest.class))).thenAnswer(i -> i.getArgument(0));

        TriageResult result = triageService.evaluate(req);
        assertEquals(List.of("UNVERIFIED_HIGH_UNITS", "SUSPICIOUS_HOSPITAL_NAME", "UNVERIFIED_CRITICAL"),
                result.reasons().stream().map(TriageResult.Reason::code).toList());
        assertEquals(90, result.score());
        assertEquals(35, result.reasons().get(0).weight());
        assertTrue(result.humanReviewRecommended());

        triageService.triageRequest(req);
        org.mockito.ArgumentCaptor<AiTriageRecord> cap = org.mockito.ArgumentCaptor.forClass(AiTriageRecord.class);
        org.mockito.Mockito.verify(recordRepository).save(cap.capture());
        assertEquals(501L, cap.getValue().getRequestId());
        assertEquals(90, cap.getValue().getScore());
        assertTrue(cap.getValue().getReasonsJson().contains("UNVERIFIED_HIGH_UNITS"));
    }

    @Test
    @DisplayName("a human review is recorded next to the AI result without zeroing the score")
    void testReview_doesNotZeroScore() {
        AiTriageRecord rec = new AiTriageRecord();
        rec.setRequestId(601L);
        rec.setScore(65);
        rec.setRawScore(65);
        rec.setReasonsJson("[]");
        when(recordRepository.findFirstByRequestIdOrderByCreatedAtDescIdDesc(601L)).thenReturn(java.util.Optional.of(rec));

        assertTrue(triageService.recordReview(601L, 7L, "dismissed", "called the hospital"));
        assertEquals(65, rec.getScore(), "the original AI score must be kept");
        assertEquals(7L, rec.getReviewedBy());
        assertNotNull(rec.getReviewedAt());
        assertEquals("DISMISSED", rec.getReviewDecision());
        assertEquals("called the hospital", rec.getReviewNote());

        when(recordRepository.findFirstByRequestIdOrderByCreatedAtDescIdDesc(602L)).thenReturn(java.util.Optional.empty());
        org.junit.jupiter.api.Assertions.assertFalse(triageService.recordReview(602L, 7L, null, null));
    }
}
