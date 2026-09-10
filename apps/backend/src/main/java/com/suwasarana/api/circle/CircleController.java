package com.suwasarana.api.circle;

import com.suwasarana.api.circle.dto.CircleInfoDto;
import com.suwasarana.api.circle.dto.CircleInviteDto;
import com.suwasarana.api.circle.dto.CircleResponseDto;
import com.suwasarana.api.circle.dto.SubmitCircleResponseDto;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Replacement Circles", description = "Private replacement donor circles, secure share tokens, and guest volunteer response coordination")
public class CircleController {

    private final CircleService circleService;

    @Autowired
    public CircleController(CircleService circleService) {
        this.circleService = circleService;
    }

    // --- Authenticated Requester Endpoints ---

    @Operation(summary = "Generate/get circle invite", description = "Creates or retrieves an existing secure tokenized invite URL for family and private circle sharing.")
    @PostMapping("/api/requests/{id}/circle")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CircleInviteDto>> createOrGetCircle(
            @PathVariable("id") Long requestId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        CircleInviteDto invite = circleService.createOrGetCircle(requestId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(invite, "Circle invite link generated successfully"));
    }

    @Operation(summary = "Get circle volunteer responses", description = "Lists all voluntary pledge responses from friends/family submitted via the private circle link.")
    @GetMapping("/api/requests/{id}/circle/responses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CircleResponseDto>>> getCircleResponses(
            @PathVariable("id") Long requestId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<CircleResponseDto> responses = circleService.getCircleResponses(requestId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    // --- Public Guest Endpoints ---

    @Operation(summary = "Get public circle information", description = "Public endpoint returning non-sensitive emergency requisition info (blood type, hospital, urgency) for guest landing pages.")
    @GetMapping("/api/circle/{token}")
    public ResponseEntity<ApiResponse<CircleInfoDto>> getCirclePublicInfo(@PathVariable("token") String token) {
        CircleInfoDto info = circleService.getCircleInfo(token);
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    @Operation(summary = "Submit guest volunteer pledge", description = "Public endpoint allowing friends or family to volunteer name, phone, and blood type without requiring an account.")
    @PostMapping("/api/circle/{token}/respond")
    public ResponseEntity<ApiResponse<CircleResponseDto>> submitCircleResponse(
            @PathVariable("token") String token,
            @Valid @RequestBody SubmitCircleResponseDto dto) {
        CircleResponseDto response = circleService.submitResponse(token, dto);
        return ResponseEntity.ok(ApiResponse.success(response, "Thank you for volunteering. The requester has been notified."));
    }
}
