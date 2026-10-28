package com.suwasarana.api.matching;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestLifecycleService;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.shared.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalationSchedulerTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private MatchingEngine matchingEngine;

    @Mock
    private RequestLifecycleService lifecycle;

    @Mock
    private MatchTimeoutProcessor matchTimeoutProcessor;

    @Spy
    private AppClock clock = AppClock.system();

    @InjectMocks
    private EscalationProcessor escalationProcessor;

    private EscalationScheduler escalationScheduler;

    private BloodRequest criticalRequest;
    private BloodRequest routineRequest;

    private LocalDateTime now() {
        return clock.utcNow();
    }

    @BeforeEach
    void setUp() {
        escalationScheduler = new EscalationScheduler();
        ReflectionTestUtils.setField(escalationScheduler, "requestRepository", requestRepository);
        ReflectionTestUtils.setField(escalationScheduler, "escalationProcessor", escalationProcessor);
        ReflectionTestUtils.setField(escalationScheduler, "matchTimeoutProcessor", matchTimeoutProcessor);

        criticalRequest = new BloodRequest();
        criticalRequest.setId(1L);
        criticalRequest.setUrgency(Urgency.CRITICAL);
        criticalRequest.setStatus(RequestStatus.ESCALATING);
        criticalRequest.setCurrentRadiusKm((short) 5);
        criticalRequest.setExpiresAt(now().plusHours(5));
        // Last escalated 10 minutes ago (past the CRITICAL 5-min threshold)
        criticalRequest.setLastEscalatedAt(now().minusMinutes(10));

        routineRequest = new BloodRequest();
        routineRequest.setId(2L);
        routineRequest.setUrgency(Urgency.ROUTINE);
        routineRequest.setStatus(RequestStatus.ESCALATING);
        routineRequest.setCurrentRadiusKm((short) 5);
        routineRequest.setExpiresAt(now().plusHours(5));
        // Last escalated 5 minutes ago (under the ROUTINE 20-min threshold)
        routineRequest.setLastEscalatedAt(now().minusMinutes(5));
    }

    @Test
    void processEscalations_criticalRequest_shouldEscalateImmediately() {
        when(requestRepository.findLiveRequests()).thenReturn(List.of(criticalRequest));
        when(requestRepository.findById(1L)).thenReturn(Optional.of(criticalRequest));

        escalationScheduler.processEscalations();

        verify(requestRepository).save(criticalRequest);
        verify(matchingEngine).runMatchingForRequest(1L);
        assertEquals(15, criticalRequest.getCurrentRadiusKm());
    }

    @Test
    void processEscalations_routineRequest_notYetDue_shouldNotEscalate() {
        when(requestRepository.findLiveRequests()).thenReturn(List.of(routineRequest));
        when(requestRepository.findById(2L)).thenReturn(Optional.of(routineRequest));

        escalationScheduler.processEscalations();

        verify(requestRepository, never()).save(any());
        verify(matchingEngine, never()).runMatchingForRequest(any());
    }

    @Test
    void processEscalations_expiredRequest_isExpiredThroughTheLifecycle() {
        criticalRequest.setExpiresAt(now().minusMinutes(1));
        when(requestRepository.findLiveRequests()).thenReturn(List.of(criticalRequest));
        when(requestRepository.findById(1L)).thenReturn(Optional.of(criticalRequest));

        escalationScheduler.processEscalations();

        verify(lifecycle).expire(criticalRequest);
        verify(matchingEngine, never()).runMatchingForRequest(any());
    }

    @Test
    void processEscalations_matchedRequestPastExpiry_expires() {
        criticalRequest.setStatus(RequestStatus.MATCHED);
        criticalRequest.setExpiresAt(now().minusMinutes(1));
        when(requestRepository.findLiveRequests()).thenReturn(List.of(criticalRequest));
        when(requestRepository.findById(1L)).thenReturn(Optional.of(criticalRequest));

        escalationScheduler.processEscalations();

        verify(lifecycle).expire(criticalRequest);
    }

    @Test
    void processEscalations_matchedRequestWithUnconfirmedDonor_reopensAndRematches() {
        criticalRequest.setStatus(RequestStatus.MATCHED);
        criticalRequest.setMatchedAt(now().minusHours(6));
        when(requestRepository.findLiveRequests()).thenReturn(List.of(criticalRequest));
        when(requestRepository.findById(1L)).thenReturn(Optional.of(criticalRequest));
        when(lifecycle.confirmationWindowElapsed(criticalRequest)).thenReturn(true);

        escalationScheduler.processEscalations();

        verify(lifecycle).lapseUnconfirmed(criticalRequest);
        verify(matchingEngine).runMatchingForRequest(1L);
    }

    @Test
    void processEscalations_matchedRequestInsideWindow_isLeftAlone() {
        criticalRequest.setStatus(RequestStatus.MATCHED);
        criticalRequest.setMatchedAt(now().minusMinutes(20));
        when(requestRepository.findLiveRequests()).thenReturn(List.of(criticalRequest));
        when(requestRepository.findById(1L)).thenReturn(Optional.of(criticalRequest));
        when(lifecycle.confirmationWindowElapsed(criticalRequest)).thenReturn(false);

        escalationScheduler.processEscalations();

        verify(lifecycle, never()).lapseUnconfirmed(any());
        verify(lifecycle, never()).expire(any());
        verify(matchingEngine, never()).runMatchingForRequest(any());
        // a matched request is never widened in radius
        assertEquals(5, criticalRequest.getCurrentRadiusKm());
    }

    @Test
    void processEscalations_alsoRunsTheMatchResponseTimeoutJob() {
        when(requestRepository.findLiveRequests()).thenReturn(List.of());

        escalationScheduler.processEscalations();

        verify(matchTimeoutProcessor).markTimedOutMatches();
    }

    @Test
    void processEscalations_oneRequestFailing_shouldStillProcessOthers() {
        BloodRequest secondCritical = new BloodRequest();
        secondCritical.setId(3L);
        secondCritical.setUrgency(Urgency.CRITICAL);
        secondCritical.setStatus(RequestStatus.ESCALATING);
        secondCritical.setCurrentRadiusKm((short) 5);
        secondCritical.setExpiresAt(now().plusHours(5));
        secondCritical.setLastEscalatedAt(now().minusMinutes(10));

        when(requestRepository.findLiveRequests()).thenReturn(List.of(criticalRequest, secondCritical));
        when(requestRepository.findById(1L)).thenReturn(Optional.of(criticalRequest));
        when(requestRepository.findById(3L)).thenReturn(Optional.of(secondCritical));
        doThrow(new RuntimeException("boom")).when(matchingEngine).runMatchingForRequest(1L);

        escalationScheduler.processEscalations();

        verify(matchingEngine).runMatchingForRequest(3L);
    }
}
