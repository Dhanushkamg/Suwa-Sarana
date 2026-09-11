package com.suwasarana.api.donor;

import com.suwasarana.api.donor.dto.AvailabilityDto;
import com.suwasarana.api.donor.dto.DonorProfileDto;
import com.suwasarana.api.donor.dto.UpdateDonorProfileDto;
import com.suwasarana.api.matching.RequestMatch;
import com.suwasarana.api.matching.RequestMatchRepository;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class DonorService {

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestMatchRepository requestMatchRepository;

    public DonorProfileDto getDonorProfile(Long userId) {
        DonorProfile profile = donorRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Donor profile not found"));
        return mapToDto(profile);
    }

    public DonorProfileDto upsertDonorProfile(Long userId, UpdateDonorProfileDto updateDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        DonorProfile profile = donorRepository.findByUserId(userId).orElse(new DonorProfile());
        
        if (profile.getId() == null) {
            profile.setUser(user);
            profile.setReliabilityScore(new BigDecimal("50.00")); // Base score
            profile.setAvailable(true);
        }

        profile.setBloodType(updateDto.getBloodType());
        profile.setDateOfBirth(updateDto.getDateOfBirth());
        profile.setWeightKg(updateDto.getWeightKg());
        profile.setDistrict(updateDto.getDistrict());
        profile.setLatitude(updateDto.getLatitude());
        profile.setLongitude(updateDto.getLongitude());
        profile.setQuietHoursStart(updateDto.getQuietHoursStart());
        profile.setQuietHoursEnd(updateDto.getQuietHoursEnd());

        DonorProfile savedProfile = donorRepository.save(profile);
        return mapToDto(savedProfile);
    }

    public DonorProfileDto updateAvailability(Long userId, AvailabilityDto availabilityDto) {
        DonorProfile profile = donorRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Donor profile not found"));
        
        profile.setAvailable(availabilityDto.getIsAvailable());
        DonorProfile savedProfile = donorRepository.save(profile);
        return mapToDto(savedProfile);
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

    public java.util.List<RequestMatch> getMyMatches(Long userId) {
        DonorProfile profile = donorRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Donor profile not found"));
        return requestMatchRepository.findByDonorId(profile.getId());
    }
}
