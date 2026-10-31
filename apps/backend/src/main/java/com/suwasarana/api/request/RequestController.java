package com.suwasarana.api.request;

import com.suwasarana.api.ai.RequestIntakeAiService;
import com.suwasarana.api.ai.dto.AiDraftRequestDto;
import com.suwasarana.api.ai.dto.AiDraftResponseDto;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.matching.MatchService;
import com.suwasarana.api.request.dto.CloseRequestDto;
import com.suwasarana.api.request.dto.CreateRequestDto;
import com.suwasarana.api.request.dto.ReportRequestDto;
import com.suwasarana.api.request.dto.RequestMatchesDto;
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

    @Autowired
    private MatchService matchService;

    @Autowired
    private RequestReportService reportService;

    @Operation(summary = "Create blood request", description = "Submits a new emergency blood requisition and triggers matching and AI triage. "
            + "The district must be one of the 25 official names and the location inside Sri Lanka. Limits: duplicate active request (409 REQUEST_DUPLICATE), too many active requests (429 REQUEST_LIMIT_REACHED).")
    @PostMapping
    public ResponseEntity<ApiResponse<RequestResponseDto>> createRequest(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreateRequestDto dto) {

        RequestResponseDto created = requestService.createRequest(userDetails.getId(), dto, isAdmin(userDetails));
        return ResponseEntity.ok(ApiResponse.success(created, "Blood request created successfully"));
    }

    @Operation(summary = "Extract AI draft from natural language", description = "Parses unstructured free-text in EN/SI/TA into structured draft parameters without creating a request.")
    @PostMapping("/ai-draft")
    public ResponseEntity<ApiResponse<AiDraftResponseDto>> extractAiDraft(
            @Valid @RequestBody AiDraftRequestDto dto) {
        AiDraftResponseDto draft = requestIntakeAiService.extractDraft(dto);
        return ResponseEntity.ok(ApiResponse.success(draft, "AI draft extracted successfully"));
    }

    @Operation(summary = "Browse active requests", description = "Admins get every request. Everyone else gets only requests that still need donors, with the location rounded to about 1 km (coarseLocation=true).")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RequestResponseDto>>> getAllRequests(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<RequestResponseDto> requests = requestService.getAllRequests(isAdmin(userDetails));
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @Operation(summary = "Get my created requests", description = "Lists all blood requests created by the authenticated requester or hospital.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<RequestResponseDto>>> getMyRequests(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<RequestResponseDto> requests = requestService.getUserRequests(userDetails.getId(), isAdmin(userDetails));
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @Operation(summary = "Get blood request by ID", description = "Visible to the request owner, admins, and donors who were matched to it. Anyone else gets 404.")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RequestResponseDto>> getRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        RequestResponseDto request = requestService.getRequestById(id, userDetails.getId(), isAdmin(userDetails));
        return ResponseEntity.ok(ApiResponse.success(request));
    }

    @Operation(summary = "Donors matched to a request (owner/admin)",
            description = "First name, blood type, distance and status of each alerted donor. The phone number appears ONLY for a donor whose match is ACCEPTED and whose outcome is not yet recorded.")
    @GetMapping("/{id}/matches")
    public ResponseEntity<ApiResponse<RequestMatchesDto>> getRequestMatches(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(ApiResponse.success(matchService.listMatchesForRequest(id, userDetails.getId(), isAdmin(userDetails))));
    }

    @Operation(summary = "Mark a request fulfilled", description = "Owner or admin states the need has been met. Only OPEN, ESCALATING or MATCHED requests (else 409 request.already_closed).")
    @PostMapping("/{id}/fulfil")
    public ResponseEntity<ApiResponse<RequestResponseDto>> fulfilRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        RequestResponseDto dto = requestService.fulfilRequest(userDetails.getId(), id, isAdmin(userDetails));
        return ResponseEntity.ok(ApiResponse.success(dto, "Request marked as fulfilled"));
    }

    @Operation(summary = "Close a request that is no longer needed", description = "Optional body {\"reason\":\"NO_LONGER_NEEDED|FOUND_DONOR_ELSEWHERE|OTHER\"}. Status becomes CANCELLED; alerted donors are told they need not go.")
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<RequestResponseDto>> closeRequest(
            @PathVariable Long id,
            @RequestBody(required = false) CloseRequestDto body,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        RequestResponseDto dto = requestService.closeRequest(userDetails.getId(), id, isAdmin(userDetails),
                body == null ? null : body.getReason());
        return ResponseEntity.ok(ApiResponse.success(dto, "Request closed"));
    }

    @Operation(summary = "Cancel blood request", description = "Cancels an active blood requisition by its owner or an administrator (same effect as /close).")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        requestService.cancelRequest(userDetails.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Request cancelled successfully"));
    }

    @Operation(summary = "Report a request", description = "Any signed-in user, once per request. Reason is an enum; details are capped at 500 characters.")
    @PostMapping("/{id}/report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> reportRequest(
            @PathVariable Long id,
            @Valid @RequestBody ReportRequestDto dto,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        reportService.report(id, userDetails.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success(null, "Report received"));
    }

    private boolean isAdmin(UserDetailsImpl userDetails) {
        return userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
