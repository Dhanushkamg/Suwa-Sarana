package com.suwasarana.api.matching;

import com.suwasarana.api.donor.DeferralService;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.donor.EligibilityResult;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.shared.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingEngineTest {

    @Mock private MatchingEngineRepository matchingEngineRepository;
    @Mock private RequestMatchRepository requestMatchRepository;
    @Mock private RequestRepository requestRepository;
    @Mock private DonorRepository donorRepository;
    @Mock private DeferralService deferralService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Spy private AppClock clock = AppClock.system();

    @InjectMocks
    private MatchingEngine matchingEngine;

    private BloodRequest request;

    @BeforeEach
    void setUp() {
        request = new BloodRequest();
        request.setId(1L);
        request.setPatientBloodType("A+");
        request.setLatitude(6.9271);
        request.setLongitude(79.8612);
        request.setCurrentRadiusKm((short) 10);
        request.setUrgency(Urgency.URGENT);
        request.setStatus(RequestStatus.ESCALATING);
    }

    private DonorProfile donor(long id) {
        DonorProfile d = new DonorProfile();
        d.setId(id);
        return d;
    }

    private Object[] row(long donorId, double km) {
        return new Object[]{donorId, km, new BigDecimal("50.00")};
    }

    private void stubCandidates(Object[]... rows) {
        when(matchingEngineRepository.findCandidateRows(anyList(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(),
                anyBoolean(), anyInt())).thenReturn(List.of(rows));
        List<DonorProfile> profiles = new ArrayList<>();
        for (Object[] r : rows) profiles.add(donor(((Number) r[0]).longValue()));
        when(donorRepository.findAllById(anyList())).thenReturn(profiles);
    }

    @Test
    void searchesAllCompatibleBloodTypesWithinTheRadiusInMetres() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(matchingEngineRepository.findCandidateRows(anyList(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(),
                anyBoolean(), anyInt())).thenReturn(List.of());

        matchingEngine.runMatchingForRequest(1L);

        ArgumentCaptor<java.util.Collection<String>> types = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(matchingEngineRepository).findCandidateRows(types.capture(), eq("A+"), eq(6.9271), eq(79.8612),
                eq(10_000.0), eq(1L), any(), eq(false), eq(MatchingEngine.CANDIDATE_POOL));
        assertEquals(java.util.Set.of("A+", "A-", "O+", "O-"), java.util.Set.copyOf(types.getValue()));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void criticalRequestsAskForNearestFirstOrdering() {
        request.setUrgency(Urgency.CRITICAL);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(matchingEngineRepository.findCandidateRows(anyList(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(),
                anyBoolean(), anyInt())).thenReturn(List.of());

        matchingEngine.runMatchingForRequest(1L);

        verify(matchingEngineRepository).findCandidateRows(anyList(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(),
                eq(true), anyInt());
    }

    @Test
    void storesTheDistanceAndKeepsTheRepositoryOrder() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        stubCandidates(row(7, 1.234), row(5, 4.5));
        when(deferralService.checkEligibility(any(DonorProfile.class), any())).thenReturn(EligibilityResult.eligible());
        when(requestMatchRepository.save(any(RequestMatch.class))).thenAnswer(inv -> {
            RequestMatch m = inv.getArgument(0);
            m.setId(m.getDonor().getId() * 100);
            return m;
        });

        matchingEngine.runMatchingForRequest(1L);

        ArgumentCaptor<RequestMatch> saved = ArgumentCaptor.forClass(RequestMatch.class);
        verify(requestMatchRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(7L, saved.getAllValues().get(0).getDonor().getId());
        assertEquals(new BigDecimal("1.23"), saved.getAllValues().get(0).getDistanceKm());
        assertEquals(new BigDecimal("4.50"), saved.getAllValues().get(1).getDistanceKm());
        assertEquals(MatchStatus.PENDING, saved.getAllValues().get(0).getStatus());
    }

    @Test
    void notificationsAreSentOnlyThroughAnAfterCommitEvent() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        stubCandidates(row(7, 1.0));
        when(deferralService.checkEligibility(any(DonorProfile.class), any())).thenReturn(EligibilityResult.eligible());
        when(requestMatchRepository.save(any(RequestMatch.class))).thenAnswer(inv -> {
            RequestMatch m = inv.getArgument(0);
            m.setId(700L);
            return m;
        });

        matchingEngine.runMatchingForRequest(1L);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        DonorsMatchedEvent e = (DonorsMatchedEvent) event.getValue();
        assertEquals(1L, e.requestId());
        assertEquals(List.of(700L), e.matchIds());
    }

    @Test
    void ineligibleDonorsAreSkipped() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        stubCandidates(row(7, 1.0), row(8, 2.0));
        when(deferralService.checkEligibility(any(DonorProfile.class), any())).thenAnswer(inv -> {
            DonorProfile d = inv.getArgument(0);
            return d.getId() == 7L ? EligibilityResult.ineligible(List.of("UNDERWEIGHT")) : EligibilityResult.eligible();
        });
        when(requestMatchRepository.save(any(RequestMatch.class))).thenAnswer(inv -> {
            RequestMatch m = inv.getArgument(0);
            m.setId(800L);
            return m;
        });

        matchingEngine.runMatchingForRequest(1L);

        ArgumentCaptor<RequestMatch> saved = ArgumentCaptor.forClass(RequestMatch.class);
        verify(requestMatchRepository).save(saved.capture());
        assertEquals(8L, saved.getValue().getDonor().getId());
    }

    @Test
    void atMostTwentyDonorsAreMatchedPerRun() {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        Object[][] rows = new Object[30][];
        for (int i = 0; i < 30; i++) rows[i] = row(i + 1, i + 0.5);
        stubCandidates(rows);
        when(deferralService.checkEligibility(any(DonorProfile.class), any())).thenReturn(EligibilityResult.eligible());
        when(requestMatchRepository.save(any(RequestMatch.class))).thenAnswer(inv -> {
            RequestMatch m = inv.getArgument(0);
            m.setId(m.getDonor().getId());
            return m;
        });

        matchingEngine.runMatchingForRequest(1L);

        verify(requestMatchRepository, org.mockito.Mockito.times(MatchingEngine.MAX_MATCHES_PER_RUN)).save(any(RequestMatch.class));
    }

    @Test
    void closedRequestsAreNotMatched() {
        request.setStatus(RequestStatus.MATCHED);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        matchingEngine.runMatchingForRequest(1L);

        verify(matchingEngineRepository, never()).findCandidateRows(anyList(), any(), anyDouble(), anyDouble(), anyDouble(), any(), any(),
                anyBoolean(), anyInt());
        assertTrue(true);
    }
}
