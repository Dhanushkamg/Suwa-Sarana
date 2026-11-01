package com.suwasarana.api.donor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The eligibility rules are the NBTS values verified in docs/audit/kb-candidates.json:
 * age 18-60 (first-time donors up to 55), weight above 50 kg, 4 months between donations.
 */
@ExtendWith(MockitoExtension.class)
public class DeferralServiceTest {

    @Mock
    private DeferralRepository deferralRepository;

    private DeferralService deferralService;
    private DeferralRulesConfig rules;

    private DonorProfile donor;
    private final LocalDate today = LocalDate.of(2026, 10, 28);

    @BeforeEach
    void setUp() {
        // the real knowledge base: the thresholds are read from EligibilityFacts, not from constants
        rules = new DeferralRulesConfig(new com.suwasarana.api.ai.kb.EligibilityFacts(
                new com.suwasarana.api.ai.kb.KnowledgeBase(new com.suwasarana.api.ai.AiProperties())));
        deferralService = new DeferralService(deferralRepository, rules);
        donor = new DonorProfile();
        donor.setId(1L);
        // Valid by default: age 25, 60 kg, has donated before
        donor.setDateOfBirth(today.minusYears(25));
        donor.setWeightKg(new BigDecimal("60.00"));
        donor.setTotalDonations(3);
        lenient().when(deferralRepository.findActiveDeferrals(1L, today)).thenReturn(Collections.emptyList());
    }

    @Test
    void testEligibleDonor() {
        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertTrue(result.isEligible());
        assertTrue(result.getReasons().isEmpty());
        assertNull(result.getNextEligibleDate());
    }

    @Test
    void testUnderageDonor() {
        donor.setDateOfBirth(today.minusYears(17));
        EligibilityResult result = deferralService.checkEligibility(donor, today);
        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("AGE_OUT_OF_RANGE"));
    }

    @Test
    void testExactlyEighteenIsEligible() {
        donor.setDateOfBirth(today.minusYears(18));
        assertTrue(deferralService.checkEligibility(donor, today).isEligible());
    }

    @Test
    void testSixtyIsEligibleSixtyOneIsNot() {
        donor.setDateOfBirth(today.minusYears(60));
        assertTrue(deferralService.checkEligibility(donor, today).isEligible());

        donor.setDateOfBirth(today.minusYears(61));
        EligibilityResult result = deferralService.checkEligibility(donor, today);
        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("AGE_OUT_OF_RANGE"));
    }

    @Test
    void testFirstTimeDonorAboveFiftyFiveIsRejected() {
        donor.setTotalDonations(0);
        donor.setDateOfBirth(today.minusYears(55));
        assertTrue(deferralService.checkEligibility(donor, today).isEligible());

        donor.setDateOfBirth(today.minusYears(56));
        EligibilityResult result = deferralService.checkEligibility(donor, today);
        assertFalse(result.isEligible());
        assertEquals(List.of("AGE_ABOVE_FIRST_TIME_LIMIT"), result.getReasons());
    }

    @Test
    void testRegularDonorAtFiftyEightIsEligible() {
        donor.setTotalDonations(2);
        donor.setDateOfBirth(today.minusYears(58));
        assertTrue(deferralService.checkEligibility(donor, today).isEligible());
    }

    @Test
    void testAgeIsEvaluatedOnTheGivenDate_notToday() {
        // 17 today, but 18 on the slot date next month
        donor.setDateOfBirth(today.minusYears(18).plusDays(10));
        assertFalse(deferralService.checkEligibility(donor, today).isEligible());
        LocalDate slotDate = today.plusDays(20);
        lenient().when(deferralRepository.findActiveDeferrals(1L, slotDate)).thenReturn(Collections.emptyList());
        assertTrue(deferralService.checkEligibility(donor, slotDate).isEligible());
    }

    @Test
    void testUnderweightDonor() {
        donor.setWeightKg(new BigDecimal("49.00"));
        EligibilityResult result = deferralService.checkEligibility(donor, today);
        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("UNDERWEIGHT"));
    }

    @Test
    void testExactlyFiftyKilogramsIsNotEnough_mustBeAbove() {
        donor.setWeightKg(new BigDecimal("50.00"));
        assertTrue(deferralService.checkEligibility(donor, today).getReasons().contains("UNDERWEIGHT"));

        donor.setWeightKg(new BigDecimal("50.01"));
        assertTrue(deferralService.checkEligibility(donor, today).isEligible());
    }

    @Test
    void testUnknownWeightIsNotAFailure() {
        donor.setWeightKg(null);
        assertTrue(deferralService.checkEligibility(donor, today).isEligible());
    }

    @Test
    void testRecentDonationDeferralGivesNextEligibleDate() {
        LocalDate donated = today.minusMonths(1);
        DonorDeferral recent = new DonorDeferral();
        recent.setReason(DeferralService.RECENT_DONATION);
        recent.setDeferredFrom(donated);
        recent.setDeferredUntil(rules.deferralEndAfterDonation(donated));
        when(deferralRepository.findActiveDeferrals(1L, today)).thenReturn(List.of(recent));

        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertFalse(result.isEligible());
        assertEquals(List.of("RECENT_DONATION"), result.getReasons());
        // four calendar months after the donation
        assertEquals(donated.plusMonths(4), result.getNextEligibleDate());
    }

    @Test
    void testOtherDeferralIsReportedAsActiveDeferral() {
        DonorDeferral other = new DonorDeferral();
        other.setReason("MEDICAL_REVIEW");
        other.setDeferredUntil(today.plusDays(9));
        when(deferralRepository.findActiveDeferrals(1L, today)).thenReturn(List.of(other));

        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertEquals(List.of("ACTIVE_DEFERRAL"), result.getReasons());
        assertEquals(today.plusDays(10), result.getNextEligibleDate());
    }

    @Test
    void testNoNextDateWhenAgeAlsoBlocks() {
        donor.setDateOfBirth(today.minusYears(16));
        DonorDeferral recent = new DonorDeferral();
        recent.setReason(DeferralService.RECENT_DONATION);
        recent.setDeferredUntil(today.plusDays(30));
        when(deferralRepository.findActiveDeferrals(1L, today)).thenReturn(List.of(recent));

        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertFalse(result.isEligible());
        assertNull(result.getNextEligibleDate(), "waiting does not fix an age problem");
    }

    @Test
    void testMultipleFailureReasons() {
        donor.setDateOfBirth(today.minusYears(16));
        donor.setWeightKg(new BigDecimal("45.00"));

        EligibilityResult result = deferralService.checkEligibility(donor, today);

        assertFalse(result.isEligible());
        assertEquals(2, result.getReasons().size());
        assertTrue(result.getReasons().contains("AGE_OUT_OF_RANGE"));
        assertTrue(result.getReasons().contains("UNDERWEIGHT"));
    }

    @Test
    void testIntervalHelpersAgreeWithTheDeferralRow() {
        LocalDate donated = LocalDate.of(2026, 1, 31);
        assertEquals(LocalDate.of(2026, 5, 31), rules.nextEligibleDateAfterDonation(donated));
        assertEquals(LocalDate.of(2026, 5, 30), rules.deferralEndAfterDonation(donated));
        // a deferral that ends on day X blocks X (deferred_until >= date) and frees X+1
        assertEquals(donated.plusMonths(4), rules.deferralEndAfterDonation(donated).plusDays(1));
    }

    @Test
    void testRulesAreTheKnowledgeBaseFacts() {
        com.suwasarana.api.ai.kb.EligibilityFacts f = new com.suwasarana.api.ai.kb.EligibilityFacts(
                new com.suwasarana.api.ai.kb.KnowledgeBase(new com.suwasarana.api.ai.AiProperties()));
        assertEquals(f.getMinAge(), rules.minAge());
        assertEquals(f.getMaxAge(), rules.maxAge());
        assertEquals(f.getFirstTimeMaxAge(), rules.firstTimeMaxAge());
        assertEquals(0, f.getWeightKgAbove().compareTo(rules.minWeightKgExclusive()));
        assertEquals(f.getDonationIntervalMonths(), rules.donationIntervalMonths());
        // the endpoint shows exactly these numbers and nothing that has no source
        assertEquals(4, rules.verifiedRules().get("donationIntervalMonths"));
        assertFalse(rules.verifiedRules().keySet().stream().anyMatch(k -> k.matches("(?i).*(tattoo|surgery|dental|antibiotic|alcohol|pregnan).*")));
    }
}
