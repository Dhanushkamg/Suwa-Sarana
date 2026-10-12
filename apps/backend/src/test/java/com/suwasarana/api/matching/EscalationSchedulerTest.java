package com.suwasarana.api.matching;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EscalationSchedulerTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private MatchingEngine matchingEngine;

    @InjectMocks
    private EscalationProcessor escalationProcessor;

    private EscalationScheduler escalationScheduler;

    private BloodRequest criticalRequest;
    private BloodRequest routineRequest;

    @BeforeEach
    void setUp() {
        escalationScheduler = new EscalationScheduler();
        ReflectionTestUtils.setField(escalationScheduler, "requestRepository", requestRepository);
        ReflectionTestUtils.setField(escalationScheduler, "escalationProcessor", escalationProcessor);

        criticalRequest = new BloodRequest();
        criticalRequest.setId(1L);
        criticalRequest.setUrgency(Urgency.CRITICAL);
        criticalRequest.setStatus(RequestStatus.ESCALATING);
        criticalRequest.setCurrentRadiusKm((short) 5);
        criticalRequest.setExpiresAt(LocalDateTime.now().plusHours(5));
        // Last escalated 10 minutes ago (past the CRITICAL 5-min threshold)
        criticalRequest.setLastEscalatedAt(LocalDateTime.now().minusMinutes(10));

        routineRequest = new BloodRequest();
        routineRequest.setId(2L);
        routineRequest.setUrgency(Urgency.ROUTINE);
        routineRequest.setStatus(RequestStatus.ESCALATING);
        routineRequest.setCurrentRadiusKm((short) 5);
        routineRequest.setExpiresAt(LocalDateTime.now().plusHours(5));
        // Last escalated 5 minutes ago (under the ROUTINE 20-min threshold)
        routineRequest.setLastEscalatedAt(LocalDateTime.now().minusMinutes(5));
    }

    @Test
    void processEscalations_criticalRequest_shouldEscalateImmediately() {
        when(requestRepository.findActiveRequests()).thenReturn(List.of(criticalRequest));
        when(requestRepository.findById(1L)).thenReturn(Optional.of(criticalRequest));

        escalationScheduler.processEscalations();

        verify(requestRepository).save(criticalRequest);
        verify(matchingEngine).runMatchingForRequest(1L);
    }

    @Test
    void processEscalations_routineRequest_notYetDue_shouldNotEscalate() {
        when(requestRepository.findActiveRequests()).thenReturn(List.of(routineRequest));
        when(requestRepository.findById(2L)).thenReturn(Optional.of(routineRequest));

        escalationScheduler.processEscalations();

        verify(requestRepository, never()).save(any());
        verify(matchingEngine, never()).runMatchingForRequest(any());
    }

    @Test
    void processEscalations_expiredRequest_shouldMarkExpired() {
        criticalRequest.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(requestRepository.findActiveRequests()).thenReturn(List.of(criticalRequest));
        when(requestRepository.findById(1L)).thenReturn(Optional.of(criticalRequest));

        escalationScheduler.processEscalations();

        verify(requestRepository).save(criticalRequest);
        assert criticalRequest.getStatus() == RequestStatus.EXPIRED;
        verify(matchingEngine, never()).runMatchingForRequest(any());
    }

    @Test
    void processEscalations_oneRequestFailing_shouldStillProcessOthers() {
        BloodRequest secondCritical = new BloodRequest();
        secondCritical.setId(3L);
        secondCritical.setUrgency(Urgency.CRITICAL);
        secondCritical.setStatus(RequestStatus.ESCALATING);
        secondCritical.setCurrentRadiusKm((short) 5);
        secondCritical.setExpiresAt(LocalDateTime.now().plusHours(5));
        secondCritical.setLastEscalatedAt(LocalDateTime.now().minusMinutes(10));

        when(requestRepository.findActiveRequests()).thenReturn(List.of(criticalRequest, secondCritical));
        when(requestRepository.findById(1L)).thenReturn(Optional.of(criticalRequest));
        when(requestRepository.findById(3L)).thenReturn(Optional.of(secondCritical));
        doThrow(new RuntimeException("boom")).when(matchingEngine).runMatchingForRequest(1L);

        escalationScheduler.processEscalations();

        verify(matchingEngine).runMatchingForRequest(3L);
    }
}
