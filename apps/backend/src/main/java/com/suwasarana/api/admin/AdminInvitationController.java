package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.InviteAdminDto;
import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.AuthMessages;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.security.ClientIp;
import com.suwasarana.api.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Administrator provisioning without SQL: invite by email; the invitee completes it via POST /api/auth/accept-invite. */
@RestController
@RequestMapping("/api/admin/invitations")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Invitations", description = "Invite new administrators by email")
public class AdminInvitationController {

    private final AdminUserService adminUserService;

    public AdminInvitationController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "Invite an administrator", description = "Sends a single-use link (valid 72 h) to the email address. The raw token is never returned.")
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> invite(
            @Valid @RequestBody InviteAdminDto body,
            @AuthenticationPrincipal UserDetailsImpl admin,
            HttpServletRequest request) {
        String language = body.getPreferredLanguage() != null ? body.getPreferredLanguage() : AuthMessages.languageOf(request.getLocale());
        Instant expires = adminUserService.inviteAdmin(admin.getId(), body.getEmail(), body.getFirstName(), language, ClientIp.of(request));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", body.getEmail());
        data.put("expiresAt", expires.toString());
        return ResponseEntity.ok(ApiResponse.success(data, AuthCodes.OK));
    }
}
