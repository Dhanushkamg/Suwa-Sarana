package com.suwasarana.api.donor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ReliabilityScoreService {

    @Autowired
    private DonorRepository donorRepository;

    private static final BigDecimal SUCCESSFUL_DONATION_REWARD = new BigDecimal("10.00");
    private static final BigDecimal NO_SHOW_PENALTY = new BigDecimal("20.00");
    private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
    private static final BigDecimal MIN_SCORE = new BigDecimal("0.00");

    @Transactional
    public void recordSuccessfulDonation(Long donorId) {
        DonorProfile profile = donorRepository.findById(donorId)
                .orElseThrow(() -> new RuntimeException("Donor not found"));

        BigDecimal newScore = profile.getReliabilityScore().add(SUCCESSFUL_DONATION_REWARD);
        if (newScore.compareTo(MAX_SCORE) > 0) {
            newScore = MAX_SCORE;
        }

        profile.setReliabilityScore(newScore);
        donorRepository.save(profile);
    }

    @Transactional
    public void recordNoShow(Long donorId) {
        DonorProfile profile = donorRepository.findById(donorId)
                .orElseThrow(() -> new RuntimeException("Donor not found"));

        BigDecimal newScore = profile.getReliabilityScore().subtract(NO_SHOW_PENALTY);
        if (newScore.compareTo(MIN_SCORE) < 0) {
            newScore = MIN_SCORE;
        }

        profile.setReliabilityScore(newScore);
        donorRepository.save(profile);
    }
}
