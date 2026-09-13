package com.suwasarana.api.auth;

import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.LoginDto;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User registration, login, token refresh, and session logout endpoints")
public class AuthController {

    @Autowired
    private AuthService authService;

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

    @Operation(summary = "Refresh access token", description = "Generates a new access token using an httpOnly refresh token cookie.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new RuntimeException("Refresh Token is empty!");
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
            
        authService.logout(userDetails.getId(), getClientIp(request));
        
        Cookie cookie = new Cookie("refresh_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refresh_token", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Ensure HTTPS in production
        cookie.setPath("/api/auth");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        response.addCookie(cookie);
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
