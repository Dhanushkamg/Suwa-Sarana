package com.suwasarana.api.auth;

import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.LoginDto;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.auth.dto.OtpRequestDto;
import com.suwasarana.api.auth.dto.OtpVerifyDto;
import com.suwasarana.api.auth.otp.OtpService;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User registration, login, token refresh, and session logout endpoints")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private OtpService otpService;

    // Secure=true needs HTTPS (browsers exempt localhost). SameSite must be "None" only if the
    // frontend and API live on different sites; "Lax" is right for same-site deployments.
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Operation(summary = "Register a new user", description = "Creates a new user account (Donor, Hospital, or Requester) and returns JWT tokens.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterDto registerDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.register(registerDto, getClientIp(request));
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @Operation(summary = "User login", description = "Authenticates user credentials and issues access & refresh tokens.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginDto loginDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(loginDto, getClientIp(request));
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @Operation(summary = "Send OTP", description = "Sends a 6-digit OTP to the specified phone number.")
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody OtpRequestDto otpRequestDto) {
        otpService.generateAndSendOtp(otpRequestDto.getPhoneNumber());
        return ResponseEntity.ok(ApiResponse.success(null, "OTP sent successfully"));
    }

    @Operation(summary = "Verify OTP", description = "Verifies the provided OTP for the phone number.")
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@Valid @RequestBody OtpVerifyDto otpVerifyDto) {
        boolean isValid = otpService.verifyOtp(otpVerifyDto.getPhoneNumber(), otpVerifyDto.getOtpCode());
        if (isValid) {
            return ResponseEntity.ok(ApiResponse.success(null, "OTP verified successfully"));
        } else {
            throw ApiException.badRequest("Invalid or expired OTP");
        }
    }

    @Operation(summary = "Reset password", description = "Sets a new password for the account behind a phone number verified via OTP, and signs out all devices.")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody com.suwasarana.api.auth.dto.ResetPasswordDto dto,
            HttpServletRequest request) {
        authService.resetPassword(dto, getClientIp(request));
        return ResponseEntity.ok(ApiResponse.success(null, "Password updated. Please sign in."));
    }

    @Operation(summary = "Refresh access token", description = "Generates a new access token using an httpOnly refresh token cookie.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw ApiException.unauthorized("Not signed in.");
        }

        AuthResponse authResponse = authService.refreshToken(refreshToken, getClientIp(request));
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @Operation(summary = "Logout user", description = "Invalidates the refresh token and clears authentication cookies.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.suwasarana.api.security.UserDetailsImpl userDetails,
            HttpServletRequest request,
            HttpServletResponse response) {

        // The endpoint is public so an expired session can still sign out; only revoke tokens when we know who it is.
        if (userDetails != null) {
            authService.logout(userDetails.getId(), getClientIp(request));
        }
        writeRefreshCookie(response, "", 0);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        writeRefreshCookie(response, refreshToken, 7 * 24 * 60 * 60);
    }

    private void writeRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Extracts the real client IP address.
     * Spring's ForwardedHeaderFilter (configured via server.forward-headers-strategy=framework)
     * populates RemoteAddr correctly when behind a trusted reverse proxy.
     */
    private String getClientIp(HttpServletRequest request) {
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    @Operation(summary = "Verify Google User", description = "Verifies a Google ID token and returns an auth token if the user exists, otherwise prompts for registration.")
    @PostMapping("/google/verify")
    public ResponseEntity<ApiResponse<com.suwasarana.api.auth.dto.GoogleVerifyResponse>> verifyGoogleUser(
            @Valid @RequestBody com.suwasarana.api.auth.dto.GoogleVerifyRequest requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        com.suwasarana.api.auth.dto.GoogleVerifyResponse verifyResponse = authService.verifyGoogleUser(requestDto, getClientIp(request));
        
        if (!verifyResponse.isRequiresRegistration() && verifyResponse.getAuthResponse() != null) {
            setRefreshTokenCookie(response, verifyResponse.getAuthResponse().getRefreshToken());
        }
        
        return ResponseEntity.ok(ApiResponse.success(verifyResponse));
    }

    @Operation(summary = "Register Google User", description = "Registers a new user who signed in via Google, requiring their phone number and role.")
    @PostMapping("/google/register")
    public ResponseEntity<ApiResponse<AuthResponse>> registerGoogleUser(
            @Valid @RequestBody com.suwasarana.api.auth.dto.GoogleRegisterRequest requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.registerGoogleUser(requestDto, getClientIp(request));
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }
}
