package com.suwasarana.api.donor;

import com.suwasarana.api.donor.dto.AvailabilityDto;
import com.suwasarana.api.donor.dto.DonorProfileDto;
import com.suwasarana.api.donor.dto.UpdateDonorProfileDto;
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
        dto.setLatitude(profile.getLatitude());
        dto.setLongitude(profile.getLongitude());
        dto.setAvailable(profile.isAvailable());
        dto.setReliabilityScore(profile.getReliabilityScore());
        dto.setQuietHoursStart(profile.getQuietHoursStart());
        dto.setQuietHoursEnd(profile.getQuietHoursEnd());
        return dto;
    }
}
