package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.AdminUserDto;
import com.suwasarana.api.admin.dto.PageDto;
import com.suwasarana.api.admin.dto.ReasonDto;
import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.security.ClientIp;
import com.suwasarana.api.security.UserDetailsImpl;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.VerificationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * THE verification API (the former duplicate {@code POST /api/admin/requesters/{id}/verify} is gone).
 * Lists institutional accounts (hospital, blood bank) waiting for a decision and records approve / reject with a
 * mandatory reason. Administrators cannot be touched; every decision is audited and the user is notified.
 */
@RestController
@RequestMapping("/api/admin/verifications")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Verification", description = "Approve or reject hospital / blood-bank accounts, with a mandatory reason")
public class AdminVerificationController {

    private final AdminUserService adminUserService;

    public AdminVerificationController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "Pending verifications", description = "Institutional accounts awaiting a decision, oldest first. Optional role=REQUESTER to see patient/family requesters instead. Donors are never listed.")
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PageDto<AdminUserDto>>> pending(
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PageDto.of(adminUserService.verificationQueue(VerificationStatus.PENDING, role, page, size), AdminUserDto::of)));
    }

    @Operation(summary = "Verification queue by status", description = "Same list for any status (PENDING, VERIFIED, REJECTED), to review past decisions.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageDto<AdminUserDto>>> byStatus(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PageDto.of(adminUserService.verificationQueue(status, role, page, size), AdminUserDto::of)));
    }

    @Operation(summary = "Approve verification", description = "Marks the account VERIFIED. Body: {reason} (required, 3-500 chars). The user is notified by email and SMS.")
    @PutMapping("/{userId}/approve")
    public ResponseEntity<ApiResponse<AdminUserDto>> approve(
            @PathVariable Long userId,
            @Valid @RequestBody ReasonDto body,
            @AuthenticationPrincipal UserDetailsImpl admin,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(AdminUserDto.of(
                adminUserService.decideVerification(admin.getId(), userId, true, body.getReason(), ClientIp.of(request))), AuthCodes.OK));
    }

    @Operation(summary = "Reject verification", description = "Marks the account REJECTED; the user can no longer publish. Body: {reason} (required, 3-500 chars), shown to the user.")
    @PutMapping("/{userId}/reject")
    public ResponseEntity<ApiResponse<AdminUserDto>> reject(
            @PathVariable Long userId,
            @Valid @RequestBody ReasonDto body,
            @AuthenticationPrincipal UserDetailsImpl admin,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(AdminUserDto.of(
                adminUserService.decideVerification(admin.getId(), userId, false, body.getReason(), ClientIp.of(request))), AuthCodes.OK));
    }
}
