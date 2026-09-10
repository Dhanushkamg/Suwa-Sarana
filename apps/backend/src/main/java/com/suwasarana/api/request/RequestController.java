package com.suwasarana.api.request;

import com.suwasarana.api.ai.RequestIntakeAiService;
import com.suwasarana.api.ai.dto.AiDraftRequestDto;
import com.suwasarana.api.ai.dto.AiDraftResponseDto;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.request.dto.CreateRequestDto;
import com.suwasarana.api.request.dto.RequestResponseDto;
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
@RequestMapping("/api/requests")
@PreAuthorize("hasAnyRole('REQUESTER', 'HOSPITAL_REQUESTER', 'ADMIN')")
@Tag(name = "Blood Requests", description = "Emergency blood requisition management, AI drafting, and lifecycle actions")
public class RequestController {

    @Autowired
    private RequestService requestService;

    @Autowired
    private RequestIntakeAiService requestIntakeAiService;

    @Operation(summary = "Create blood request", description = "Submits a new emergency blood requisition and triggers matching and AI triage.")
    @PostMapping
    public ResponseEntity<ApiResponse<RequestResponseDto>> createRequest(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreateRequestDto dto) {
            
        RequestResponseDto created = requestService.createRequest(userDetails.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success(created, "Blood request created successfully"));
    }

    @Operation(summary = "Extract AI draft from natural language", description = "Parses unstructured free-text in EN/SI/TA into structured draft parameters without creating a request.")
    @PostMapping("/ai-draft")
    public ResponseEntity<ApiResponse<AiDraftResponseDto>> extractAiDraft(
            @Valid @RequestBody AiDraftRequestDto dto) {
        AiDraftResponseDto draft = requestIntakeAiService.extractDraft(dto);
        return ResponseEntity.ok(ApiResponse.success(draft, "AI draft extracted successfully"));
    }

    @Operation(summary = "Get all active requests", description = "Retrieves all blood requests across districts.")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RequestResponseDto>>> getAllRequests() {
        List<RequestResponseDto> requests = requestService.getAllRequests();
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @Operation(summary = "Get my created requests", description = "Lists all blood requests created by the authenticated requester or hospital.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<RequestResponseDto>>> getMyRequests(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<RequestResponseDto> requests = requestService.getUserRequests(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @Operation(summary = "Get blood request by ID", description = "Fetches complete details and fulfillment statistics for a specific blood request.")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RequestResponseDto>> getRequest(@PathVariable Long id) {
        RequestResponseDto request = requestService.getRequestById(id);
        return ResponseEntity.ok(ApiResponse.success(request));
    }

    @Operation(summary = "Cancel blood request", description = "Cancels an active blood requisition by its owner or an administrator.")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        requestService.cancelRequest(userDetails.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Request cancelled successfully"));
    }
}
