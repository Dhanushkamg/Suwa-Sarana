package com.suwasarana.api.user;

import com.suwasarana.api.audit.AdminAuditService;
import com.suwasarana.api.audit.AuditAction;
import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.RefreshCookie;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.privacy.PrivacyService;
import com.suwasarana.api.security.ClientIp;
import com.suwasarana.api.security.UserDetailsImpl;
import com.suwasarana.api.user.dto.ChangePasswordDto;
import com.suwasarana.api.user.dto.DeleteAccountDto;
import com.suwasarana.api.user.dto.MeDto;
import com.suwasarana.api.user.dto.UpdateMeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The signed-in user's own account (any role): profile, phone change, password change, consent, export, erasure. */
@RestController
@RequestMapping("/api/users/me")
@PreAuthorize("isAuthenticated()")
@Tag(name = "My Account", description = "Profile, language, phone, password, consent, personal-data export and account deletion")
public class UserController {

    private final UserService userService;
    private final PrivacyService privacyService;
    private final AdminAuditService audit;
    private final RefreshCookie refreshCookie;

    public UserController(UserService userService, PrivacyService privacyService, AdminAuditService audit, RefreshCookie refreshCookie) {
        this.userService = userService;
        this.privacyService = privacyService;
        this.audit = audit;
        this.refreshCookie = refreshCookie;
    }

    @Operation(summary = "My profile")
    @GetMapping
    public ResponseEntity<ApiResponse<MeDto>> me(@AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(userService.me(principal.getId())));
    }

    @Operation(summary = "Update my profile", description = "Partial: firstName, lastName, preferredLanguage (en|si|ta), phoneNumber. A new phone number must first be verified with /api/auth/send-otp + /api/auth/verify-otp.")
    @PatchMapping
    public ResponseEntity<ApiResponse<MeDto>> update(@AuthenticationPrincipal UserDetailsImpl principal, @Valid @RequestBody UpdateMeDto dto) {
        return ResponseEntity.ok(ApiResponse.success(userService.update(principal.getId(), dto), AuthCodes.OK));
    }

    @Operation(summary = "Change my password", description = "Needs the current password. Signs out every other device and returns a fresh session (new access token + cookie).")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody ChangePasswordDto dto,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthResponse session = userService.changePassword(principal.getId(), dto, ClientIp.of(request));
        refreshCookie.set(response, session.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(session, AuthCodes.PASSWORD_UPDATED));
    }

    @Operation(summary = "Accept the current terms", description = "Records consent to the current terms / privacy version (for accounts created before consent was recorded, or after the terms changed).")
    @PostMapping("/consent")
    public ResponseEntity<ApiResponse<MeDto>> consent(@AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(ApiResponse.success(userService.acceptCurrentConsent(principal.getId()), AuthCodes.OK));
    }

    @Operation(summary = "Send a confirmation code to my phone", description = "Used to confirm account deletion when you do not use the password.")
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@AuthenticationPrincipal UserDetailsImpl principal, HttpServletRequest request) {
        userService.sendOwnOtp(principal.getId(), request.getLocale());
        return ResponseEntity.ok(ApiResponse.success(null, AuthCodes.OTP_SENT));
    }

    @Operation(summary = "Export everything held about me", description = "A JSON download of the profile, donor profile, requests, matches, bookings, notification logs and consent. Audited.")
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal UserDetailsImpl principal, HttpServletRequest request) {
        Map<String, Object> doc = privacyService.export(principal.getId());
        audit.record(principal.getId(), Role.valueOf(principal.getRoleName()), AuditAction.DATA_EXPORT, principal.getId(), null,
                "self-service export", ClientIp.of(request));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("suwa-sarana-my-data.json").build().toString())
                .body(doc);
    }

    @Operation(summary = "Delete my account", description = "Anonymises the account: personal details are erased, sessions end, active requests / bookings are cancelled. Body: {password} or {otpCode}. Administrators cannot self-delete.")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody(required = false) DeleteAccountDto dto,
            HttpServletRequest request,
            HttpServletResponse response) {
        String password = dto == null ? null : dto.getPassword();
        String otp = dto == null ? null : dto.getOtpCode();
        privacyService.deleteAccount(principal.getId(), password, otp, ClientIp.of(request));
        refreshCookie.clear(response);
        return ResponseEntity.ok(ApiResponse.success(null, AuthCodes.OK));
    }
}
