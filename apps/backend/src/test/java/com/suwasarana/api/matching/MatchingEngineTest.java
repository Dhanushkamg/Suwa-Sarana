package com.suwasarana.api.matching;

import com.suwasarana.api.donor.DeferralService;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.EligibilityResult;
import com.suwasarana.api.notification.NotificationCascadeService;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingEngineTest {

    @Mock
    private MatchingEngineRepository matchingEngineRepository;

    @Mock
    private RequestMatchRepository requestMatchRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private DeferralService deferralService;

    @Mock
    private NotificationCascadeService notificationCascadeService;

    @InjectMocks
    private MatchingEngine matchingEngine;

    private BloodRequest sampleRequest;
    private DonorProfile sampleDonor;

    @BeforeEach
    void setUp() {
        sampleRequest = new BloodRequest();
        sampleRequest.setId(1L);
        sampleRequest.setPatientBloodType("O+");
        sampleRequest.setLatitude(6.9271);
        sampleRequest.setLongitude(79.8612);
        sampleRequest.setCurrentRadiusKm((short) 5);
        sampleRequest.setUnitsNeeded((short) 2);
        sampleRequest.setUrgency(Urgency.URGENT);
        sampleRequest.setExpiresAt(java.time.LocalDateTime.now().plusHours(6));
        sampleRequest.setLastEscalatedAt(java.time.LocalDateTime.now());

        sampleDonor = new DonorProfile();
        sampleDonor.setId(10L);
        sampleDonor.setDateOfBirth(LocalDate.now().minusYears(28));
        sampleDonor.setWeightKg(new BigDecimal("70.00"));
        sampleDonor.setReliabilityScore(new BigDecimal("75.00"));

        com.suwasarana.api.user.User user = new com.suwasarana.api.user.User();
        user.setId(100L);
        sampleDonor.setUser(user);
    }

    @Test
    void runMatchingForRequest_eligibleDonors_shouldCreateMatchAndNotify() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(sampleRequest));
        when(matchingEngineRepository.findEligibleDonorsWithinRadius(
                eq("O+"), anyDouble(), anyDouble(), anyShort(), eq(1L), anyInt()))
                .thenReturn(List.of(sampleDonor));

        EligibilityResult eligibleResult = EligibilityResult.eligible();
        when(deferralService.checkEligibility(eq(sampleDonor), any())).thenReturn(eligibleResult);

        matchingEngine.runMatchingForRequest(1L);

        verify(requestMatchRepository, times(1)).save(any(RequestMatch.class));
        verify(notificationCascadeService, times(1)).sendNotification(eq(sampleDonor), eq(sampleRequest));
    }

    @Test
    void runMatchingForRequest_ineligibleDonor_shouldNotCreateMatch() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(sampleRequest));
        when(matchingEngineRepository.findEligibleDonorsWithinRadius(
                eq("O+"), anyDouble(), anyDouble(), anyShort(), eq(1L), anyInt()))
                .thenReturn(List.of(sampleDonor));

        EligibilityResult ineligibleResult = EligibilityResult.ineligible(List.of("UNDERWEIGHT"));
        when(deferralService.checkEligibility(eq(sampleDonor), any())).thenReturn(ineligibleResult);

        matchingEngine.runMatchingForRequest(1L);

        verify(requestMatchRepository, never()).save(any());
        verify(notificationCascadeService, never()).sendNotification(any(), any());
    }

    @Test
    void runMatchingForRequest_noCandidates_shouldNotCreateMatch() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(sampleRequest));
        when(matchingEngineRepository.findEligibleDonorsWithinRadius(
                any(), anyDouble(), anyDouble(), anyShort(), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        matchingEngine.runMatchingForRequest(1L);

        verify(requestMatchRepository, never()).save(any());
        verify(notificationCascadeService, never()).sendNotification(any(), any());
    }
}
