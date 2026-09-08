package com.suwasarana.api.donor;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeferralServiceTest {

    @Mock
    private DeferralRepository deferralRepository;

    @InjectMocks
    private DeferralService deferralService;

    private DonorProfile donor;

    @BeforeEach
    void setUp() {
        donor = new DonorProfile();
        donor.setId(1L);
        // Valid by default: Age 25, Weight 60kg
        donor.setDateOfBirth(LocalDate.now().minusYears(25));
        donor.setWeightKg(new BigDecimal("60.00"));
    }

    @Test
    void testEligibleDonor() {
        LocalDate today = LocalDate.now();
        when(deferralRepository.findActiveDeferrals(1L, today)).thenReturn(Collections.emptyList());

        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertTrue(result.isEligible());
        assertTrue(result.getReasons().isEmpty());
    }

    @Test
    void testUnderageDonor() {
        LocalDate today = LocalDate.now();
        // Age 17
        donor.setDateOfBirth(today.minusYears(17));

        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("AGE_OUT_OF_RANGE"));
    }

    @Test
    void testOverageDonor() {
        LocalDate today = LocalDate.now();
        // Age 61
        donor.setDateOfBirth(today.minusYears(61));

        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("AGE_OUT_OF_RANGE"));
    }

    @Test
    void testUnderweightDonor() {
        LocalDate today = LocalDate.now();
        // Weight 49kg
        donor.setWeightKg(new BigDecimal("49.00"));

        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("UNDERWEIGHT"));
    }

    @Test
    void testActiveDeferral() {
        LocalDate today = LocalDate.now();
        
        DonorDeferral activeDeferral = new DonorDeferral();
        activeDeferral.setReason("RECENT_DONATION");
        activeDeferral.setDeferredUntil(today.plusMonths(1));

        when(deferralRepository.findActiveDeferrals(1L, today)).thenReturn(List.of(activeDeferral));

        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("ACTIVE_DEFERRAL"));
    }

    @Test
    void testMultipleFailureReasons() {
        LocalDate today = LocalDate.now();
        donor.setDateOfBirth(today.minusYears(16)); // underage
        donor.setWeightKg(new BigDecimal("45.00")); // underweight

        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertFalse(result.isEligible());
        assertEquals(2, result.getReasons().size());
        assertTrue(result.getReasons().contains("AGE_OUT_OF_RANGE"));
        assertTrue(result.getReasons().contains("UNDERWEIGHT"));
    }
}
