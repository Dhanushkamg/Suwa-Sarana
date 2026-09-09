package com.suwasarana.api.donor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReliabilityScoreServiceTest {

    @Mock
    private DonorRepository donorRepository;

    @InjectMocks
    private ReliabilityScoreService reliabilityScoreService;

    private DonorProfile donor;

    @BeforeEach
    void setUp() {
        donor = new DonorProfile();
        donor.setId(1L);
        donor.setReliabilityScore(new BigDecimal("50.00"));
        when(donorRepository.findById(1L)).thenReturn(Optional.of(donor));
    }

    @Test
    void recordSuccessfulDonation_shouldIncreaseScore() {
        reliabilityScoreService.recordSuccessfulDonation(1L);

        assertEquals(new BigDecimal("60.00"), donor.getReliabilityScore());
        verify(donorRepository).save(donor);
    }

    @Test
    void recordSuccessfulDonation_shouldCapAt100() {
        donor.setReliabilityScore(new BigDecimal("95.00"));

        reliabilityScoreService.recordSuccessfulDonation(1L);

        assertEquals(new BigDecimal("100.00"), donor.getReliabilityScore());
    }

    @Test
    void recordNoShow_shouldDecreaseScore() {
        reliabilityScoreService.recordNoShow(1L);

        assertEquals(new BigDecimal("30.00"), donor.getReliabilityScore());
        verify(donorRepository).save(donor);
    }

    @Test
    void recordNoShow_shouldFloorAt0() {
        donor.setReliabilityScore(new BigDecimal("10.00"));

        reliabilityScoreService.recordNoShow(1L);

        assertEquals(new BigDecimal("0.00"), donor.getReliabilityScore());
    }
}
