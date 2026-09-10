package com.suwasarana.api.matching;

import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/matches")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Donor Matching", description = "Match response intake, acceptance/declination, and donation verification")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @Operation(summary = "Respond to match request", description = "Allows a notified donor to ACCEPT or DECLINE an emergency blood match requisition.")
    @PostMapping("/{id}/respond")
    @PreAuthorize("hasRole('DONOR')")
    public ResponseEntity<ApiResponse<Void>> respondToMatch(
            @PathVariable Long id,
            @RequestParam MatchStatus response,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
            
        matchService.respondToMatch(id, userDetails.getId(), response);
        return ResponseEntity.ok(ApiResponse.success(null, "Match responded successfully"));
    }

    @Operation(summary = "Mark donation completed", description = "Requester or hospital marks a matched donation as completed, updating donor reliability score.")
    @PostMapping("/{id}/donated")
    @PreAuthorize("hasAnyRole('REQUESTER', 'HOSPITAL_REQUESTER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> markDonated(
            @PathVariable Long id,
            @RequestParam boolean donated,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
            
        matchService.markMatchDonated(id, userDetails.getId(), donated);
        return ResponseEntity.ok(ApiResponse.success(null, "Donation status marked successfully"));
    }
}
