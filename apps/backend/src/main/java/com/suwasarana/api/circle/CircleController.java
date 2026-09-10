package com.suwasarana.api.circle;

import com.suwasarana.api.circle.dto.CircleInfoDto;
import com.suwasarana.api.circle.dto.CircleInviteDto;
import com.suwasarana.api.circle.dto.CircleResponseDto;
import com.suwasarana.api.circle.dto.SubmitCircleResponseDto;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.security.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CircleController {

    private final CircleService circleService;

    @Autowired
    public CircleController(CircleService circleService) {
        this.circleService = circleService;
    }

    // --- Authenticated Requester Endpoints ---

    @PostMapping("/api/requests/{id}/circle")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CircleInviteDto>> createOrGetCircle(
            @PathVariable("id") Long requestId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        CircleInviteDto invite = circleService.createOrGetCircle(requestId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(invite, "Circle invite link generated successfully"));
    }

    @GetMapping("/api/requests/{id}/circle/responses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CircleResponseDto>>> getCircleResponses(
            @PathVariable("id") Long requestId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<CircleResponseDto> responses = circleService.getCircleResponses(requestId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    // --- Public Guest Endpoints ---

    @GetMapping("/api/circle/{token}")
    public ResponseEntity<ApiResponse<CircleInfoDto>> getCirclePublicInfo(@PathVariable("token") String token) {
        CircleInfoDto info = circleService.getCircleInfo(token);
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    @PostMapping("/api/circle/{token}/respond")
    public ResponseEntity<ApiResponse<CircleResponseDto>> submitCircleResponse(
            @PathVariable("token") String token,
            @Valid @RequestBody SubmitCircleResponseDto dto) {
        CircleResponseDto response = circleService.submitResponse(token, dto);
        return ResponseEntity.ok(ApiResponse.success(response, "Thank you for volunteering. The requester has been notified."));
    }
}
