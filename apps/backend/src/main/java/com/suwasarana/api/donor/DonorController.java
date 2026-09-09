package com.suwasarana.api.donor;

import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.donor.dto.AvailabilityDto;
import com.suwasarana.api.donor.dto.DonorProfileDto;
import com.suwasarana.api.donor.dto.UpdateDonorProfileDto;
import com.suwasarana.api.security.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/donors/me")
@PreAuthorize("hasRole('DONOR')")
public class DonorController {

    @Autowired
    private DonorService donorService;

    @GetMapping
    public ResponseEntity<ApiResponse<DonorProfileDto>> getMyProfile(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        DonorProfileDto profile = donorService.getDonorProfile(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<DonorProfileDto>> updateMyProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UpdateDonorProfileDto updateDto) {
        
        DonorProfileDto profile = donorService.upsertDonorProfile(userDetails.getId(), updateDto);
        return ResponseEntity.ok(ApiResponse.success(profile, "Profile updated successfully"));
    }

    @PatchMapping("/availability")
    public ResponseEntity<ApiResponse<DonorProfileDto>> updateAvailability(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody AvailabilityDto availabilityDto) {
        
        DonorProfileDto profile = donorService.updateAvailability(userDetails.getId(), availabilityDto);
        return ResponseEntity.ok(ApiResponse.success(profile, "Availability updated successfully"));
    }

    @GetMapping("/matches")
    public ResponseEntity<ApiResponse<java.util.List<com.suwasarana.api.matching.RequestMatch>>> getMyMatches(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(ApiResponse.success(donorService.getMyMatches(userDetails.getId())));
    }
}
