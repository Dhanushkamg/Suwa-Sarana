package com.suwasarana.api.donor;

import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.donor.dto.AvailabilityDto;
import com.suwasarana.api.donor.dto.DonationHistoryDto;
import com.suwasarana.api.donor.dto.DonorProfileDto;
import com.suwasarana.api.donor.dto.EligibilityDto;
import com.suwasarana.api.donor.dto.MatchDto;
import com.suwasarana.api.donor.dto.UpdateDonorProfileDto;
import com.suwasarana.api.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/donors/me")
@PreAuthorize("hasRole('DONOR')")
@Tag(name = "Donors", description = "Donor profile management, availability toggling, match history, and deferral eligibility")
public class DonorController {

    @Autowired
    private DonorService donorService;

    @Operation(summary = "Get donor profile", description = "Retrieves current authenticated donor's profile, deferral status, and reliability metrics.")
    @GetMapping
    public ResponseEntity<ApiResponse<DonorProfileDto>> getMyProfile(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        DonorProfileDto profile = donorService.getDonorProfile(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @Operation(summary = "Update donor profile", description = "Updates blood type, location coordinates, district, and notification preferences.")
    @PutMapping
    public ResponseEntity<ApiResponse<DonorProfileDto>> updateMyProfile(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody UpdateDonorProfileDto updateDto) {
        
        DonorProfileDto profile = donorService.upsertDonorProfile(userDetails.getId(), updateDto);
        return ResponseEntity.ok(ApiResponse.success(profile, "Profile updated successfully"));
    }

    @Operation(summary = "Update donation availability", description = "Toggles donor availability status and optional temporary snooze timestamp.")
    @PatchMapping("/availability")
    public ResponseEntity<ApiResponse<DonorProfileDto>> updateAvailability(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody AvailabilityDto availabilityDto) {
        
        DonorProfileDto profile = donorService.updateAvailability(userDetails.getId(), availabilityDto);
        return ResponseEntity.ok(ApiResponse.success(profile, "Availability updated successfully"));
    }

    @Operation(summary = "Get received match requests", description = "Lists all emergency dispatch requests received by the authenticated donor.")
    @GetMapping("/matches")
    public ResponseEntity<ApiResponse<java.util.List<MatchDto>>> getMyMatches(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(ApiResponse.success(donorService.getMyMatches(userDetails.getId())));
    }

    @Operation(summary = "Donation history", description = "The donor's own recorded donations, newest first (from request confirmations and camp/hospital check-ins).")
    @GetMapping("/donations")
    public ResponseEntity<ApiResponse<java.util.List<DonationHistoryDto>>> getMyDonations(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.success(donorService.getDonationHistory(userDetails.getId(), limit)));
    }

    @Operation(summary = "Donation eligibility", description = "Whether the donor may donate today, the next eligible date and reason codes, from the single NBTS rule set.")
    @GetMapping("/eligibility")
    public ResponseEntity<ApiResponse<EligibilityDto>> getMyEligibility(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(ApiResponse.success(donorService.getEligibility(userDetails.getId())));
    }
}
