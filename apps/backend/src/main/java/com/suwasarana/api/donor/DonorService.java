package com.suwasarana.api.donor;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.donor.dto.AvailabilityDto;
import com.suwasarana.api.donor.dto.DonationHistoryDto;
import com.suwasarana.api.donor.dto.DonorProfileDto;
import com.suwasarana.api.donor.dto.EligibilityDto;
import com.suwasarana.api.donor.dto.MatchDto;
import com.suwasarana.api.donor.dto.UpdateDonorProfileDto;
import com.suwasarana.api.geo.District;
import com.suwasarana.api.geo.GeoBounds;
import com.suwasarana.api.matching.RequestMatch;
import com.suwasarana.api.matching.RequestMatchRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.shared.CodedApiException;
import com.suwasarana.api.shared.ErrorCodes;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class DonorService {

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestMatchRepository requestMatchRepository;

    @Autowired
    private DeferralService deferralService;

    @Autowired
    private DeferralRulesConfig rulesConfig;

    @Autowired
    private DonationRecordRepository donationRecordRepository;

    @Autowired
    private AppClock clock;

    public DonorProfileDto getDonorProfile(Long userId) {
        DonorProfile profile = donorRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Donor profile not found"));
        return mapToDto(profile);
    }

    public DonorProfileDto upsertDonorProfile(Long userId, UpdateDonorProfileDto updateDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        String district = District.normalise(updateDto.getDistrict())
                .orElseThrow(() -> CodedApiException.badRequest(ErrorCodes.UNKNOWN_DISTRICT,
                        "Unknown district. Use one of the 25 official district names (GET /api/meta/districts)."));
        if (!GeoBounds.contains(updateDto.getLatitude(), updateDto.getLongitude())) {
            throw CodedApiException.badRequest(ErrorCodes.OUTSIDE_SRI_LANKA, "Location must be inside Sri Lanka");
        }

        DonorProfile profile = donorRepository.findByUserId(userId).orElse(new DonorProfile());

        if (profile.getId() == null) {
            profile.setUser(user);
            profile.setReliabilityScore(new BigDecimal("50.00")); // Base score
            profile.setAvailable(true);
        }

        profile.setBloodType(updateDto.getBloodType());
        profile.setDateOfBirth(updateDto.getDateOfBirth());
        profile.setWeightKg(updateDto.getWeightKg());
        profile.setDistrict(district);
        profile.setLatitude(updateDto.getLatitude());
        profile.setLongitude(updateDto.getLongitude());
        profile.setQuietHoursStart(updateDto.getQuietHoursStart());
        profile.setQuietHoursEnd(updateDto.getQuietHoursEnd());

        DonorProfile savedProfile = donorRepository.save(profile);
        return mapToDto(savedProfile);
    }

    public DonorProfileDto updateAvailability(Long userId, AvailabilityDto availabilityDto) {
        DonorProfile profile = donorRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Donor profile not found"));

        profile.setAvailable(availabilityDto.getIsAvailable());
        DonorProfile savedProfile = donorRepository.save(profile);
        return mapToDto(savedProfile);
    }

    /** Eligibility for today, from {@link DeferralService} (which reads {@link DeferralRulesConfig}). */
    @Transactional(readOnly = true)
    public EligibilityDto getEligibility(Long userId) {
        DonorProfile profile = donorRepository.findByUserId(userId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.DONOR_PROFILE_NOT_FOUND, "Donor profile not found"));
        LocalDate today = clock.today();
        EligibilityResult result = deferralService.checkEligibility(profile, today);

        EligibilityDto dto = new EligibilityDto();
        dto.setEligible(result.isEligible());
        dto.setNextEligibleDate(result.getNextEligibleDate());
        dto.setReasons(result.getReasons());
        dto.setEvaluatedOn(today);
        dto.setLastDonationDate(donationRecordRepository.findLastDonationDate(profile.getId()).orElse(null));
        dto.setTotalDonations(profile.getTotalDonations());
        dto.setRules(rulesConfig.verifiedRules());
        dto.setRulesConfirmedByOwner(rulesConfig.isOwnerConfirmed());
        return dto;
    }

    /** The donor's own recorded donations, newest first. */
    @Transactional(readOnly = true)
    public List<DonationHistoryDto> getDonationHistory(Long userId, int limit) {
        DonorProfile profile = donorRepository.findByUserId(userId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.DONOR_PROFILE_NOT_FOUND, "Donor profile not found"));
        int size = Math.max(1, Math.min(limit, 500));
        return donationRecordRepository.findByDonorIdOrderByDonatedOnDescIdDesc(profile.getId(), PageRequest.of(0, size))
                .stream().map(DonationHistoryDto::from).toList();
    }

    private DonorProfileDto mapToDto(DonorProfile profile) {
        DonorProfileDto dto = new DonorProfileDto();
        dto.setBloodType(profile.getBloodType());
        dto.setDateOfBirth(profile.getDateOfBirth());
        dto.setWeightKg(profile.getWeightKg());
        dto.setDistrict(profile.getDistrict());

        Double lat = profile.getLatitude();
        Double lon = profile.getLongitude();

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isOwner = auth != null && profile.getUser() != null && auth.getName().equals(profile.getUser().getEmail());
        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isOwner && !isAdmin && lat != null && lon != null) {
            // Apply jitter (approx 500m ~ 0.005 degrees)
            java.util.Random rand = new java.util.Random();
            double jitterLat = (rand.nextDouble() - 0.5) * 0.01;
            double jitterLon = (rand.nextDouble() - 0.5) * 0.01;
            lat += jitterLat;
            lon += jitterLon;
        }

        dto.setLatitude(lat);
        dto.setLongitude(lon);
        dto.setAvailable(profile.isAvailable());
        dto.setReliabilityScore(profile.getReliabilityScore());
        dto.setQuietHoursStart(profile.getQuietHoursStart());
        dto.setQuietHoursEnd(profile.getQuietHoursEnd());
        dto.setTotalDonations(profile.getTotalDonations());
        dto.setLivesHelpedEstimate(profile.getLivesHelpedEstimate());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<MatchDto> getMyMatches(Long userId) {
        DonorProfile profile = donorRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Donor profile not found"));
        // Request is fetched in the same query (no N+1)
        return requestMatchRepository.findByDonorIdOrderByCreatedAtDesc(profile.getId()).stream()
                .map(this::mapToMatchDto)
                .toList();
    }

    private MatchDto mapToMatchDto(RequestMatch match) {
        BloodRequest request = match.getRequest();
        MatchDto.RequestSummary summary = new MatchDto.RequestSummary();
        summary.setId(request.getId());
        summary.setPatientBloodType(request.getPatientBloodType());
        summary.setUrgency(request.getUrgency());
        summary.setHospitalName(request.getHospitalName());
        summary.setDistrict(request.getDistrict());
        summary.setUnitsNeeded(request.getUnitsNeeded());
        summary.setStatus(request.getStatus());
        summary.setExpiresAt(request.getExpiresAt());
        summary.setLatitude(request.getLatitude());
        summary.setLongitude(request.getLongitude());
        summary.setCurrentRadiusKm(request.getCurrentRadiusKm());

        MatchDto dto = new MatchDto();
        dto.setId(match.getId());
        dto.setStatus(match.getStatus());
        dto.setCreatedAt(match.getCreatedAt());
        dto.setRespondedAt(match.getRespondedAt());
        dto.setDistanceKm(match.getDistanceKm());
        dto.setRequest(summary);
        return dto;
    }
}
