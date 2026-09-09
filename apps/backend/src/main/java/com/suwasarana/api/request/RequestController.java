package com.suwasarana.api.request;

import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.request.dto.CreateRequestDto;
import com.suwasarana.api.request.dto.RequestResponseDto;
import com.suwasarana.api.security.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/requests")
@PreAuthorize("hasAnyRole('REQUESTER', 'HOSPITAL_REQUESTER', 'ADMIN')")
public class RequestController {

    @Autowired
    private RequestService requestService;

    @PostMapping
    public ResponseEntity<ApiResponse<RequestResponseDto>> createRequest(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreateRequestDto dto) {
            
        RequestResponseDto created = requestService.createRequest(userDetails.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success(created, "Blood request created successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()") // Anyone authenticated can view a request details
    public ResponseEntity<ApiResponse<RequestResponseDto>> getRequest(@PathVariable Long id) {
        RequestResponseDto request = requestService.getRequestById(id);
        return ResponseEntity.ok(ApiResponse.success(request));
    }
}
