package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.AdminUserDto;
import com.suwasarana.api.admin.dto.PageDto;
import com.suwasarana.api.admin.dto.ReasonDto;
import com.suwasarana.api.admin.dto.RoleChangeDto;
import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.security.ClientIp;
import com.suwasarana.api.security.UserDetailsImpl;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Moderation of accounts: search, suspend / restore, role change, data-subject export. ADMIN only; admins are untouchable. */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Users", description = "Search accounts, suspend / restore, change role, export personal data. Every action needs a reason and is audited.")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "Search users", description = "Filters: q (email / phone / name), role, status (ACTIVE | SUSPENDED | DELETED).")
    @GetMapping
    public ResponseEntity<ApiResponse<PageDto<AdminUserDto>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                PageDto.of(adminUserService.search(q, role, status, page, size), AdminUserDto::of)));
    }

    @Operation(summary = "Suspend a user", description = "Blocks sign-in and invalidates every session immediately. Body: {reason} (required). Not allowed on administrators.")
    @PostMapping("/{userId}/suspend")
    public ResponseEntity<ApiResponse<AdminUserDto>> suspend(
            @PathVariable Long userId, @Valid @RequestBody ReasonDto body,
            @AuthenticationPrincipal UserDetailsImpl admin, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(AdminUserDto.of(
                adminUserService.suspend(admin.getId(), userId, body.getReason(), ClientIp.of(request))), AuthCodes.OK));
    }

    @Operation(summary = "Restore a suspended user", description = "Body: {reason} (required).")
    @PostMapping("/{userId}/restore")
    public ResponseEntity<ApiResponse<AdminUserDto>> restore(
            @PathVariable Long userId, @Valid @RequestBody ReasonDto body,
            @AuthenticationPrincipal UserDetailsImpl admin, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(AdminUserDto.of(
                adminUserService.restore(admin.getId(), userId, body.getReason(), ClientIp.of(request))), AuthCodes.OK));
    }

    @Operation(summary = "Change a user's role", description = "Body: {role, reason}. ADMIN can never be granted or changed here (use invitations). Verification restarts at PENDING.")
    @PutMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<AdminUserDto>> changeRole(
            @PathVariable Long userId, @Valid @RequestBody RoleChangeDto body,
            @AuthenticationPrincipal UserDetailsImpl admin, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(AdminUserDto.of(
                adminUserService.changeRole(admin.getId(), userId, body.getRole(), body.getReason(), ClientIp.of(request))), AuthCodes.OK));
    }

    @Operation(summary = "Export a user's personal data", description = "For a data-subject request. Query param: reason (required). Audited.")
    @GetMapping("/{userId}/export")
    public ResponseEntity<Map<String, Object>> exportData(
            @PathVariable Long userId, @RequestParam String reason,
            @AuthenticationPrincipal UserDetailsImpl admin, HttpServletRequest request) {
        Map<String, Object> doc = adminUserService.exportUserData(admin.getId(), userId, reason, ClientIp.of(request));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("user-" + userId + "-data.json").build().toString())
                .body(doc);
    }
}
