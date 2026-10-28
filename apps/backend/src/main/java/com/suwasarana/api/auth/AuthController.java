package com.suwasarana.api.auth;

import com.suwasarana.api.auth.dto.AcceptInviteDto;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.ForgotPasswordDto;
import com.suwasarana.api.auth.dto.GoogleLinkRequest;
import com.suwasarana.api.auth.dto.GoogleRegisterRequest;
import com.suwasarana.api.auth.dto.GoogleVerifyRequest;
import com.suwasarana.api.auth.dto.GoogleVerifyResponse;
import com.suwasarana.api.auth.dto.LoginDto;
import com.suwasarana.api.auth.dto.OtpRequestDto;
import com.suwasarana.api.auth.dto.OtpVerifyDto;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.auth.dto.ResetPasswordDto;
import com.suwasarana.api.auth.dto.ResetPasswordTokenDto;
import com.suwasarana.api.auth.dto.VerifyEmailDto;
import com.suwasarana.api.auth.otp.OtpService;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.security.ClientIp;
import com.suwasarana.api.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, login, OTP, password reset, email verification, Google sign-in, token refresh and logout")
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final OtpService otpService;
    private final RefreshCookie refreshCookie;
    private final OriginGuard originGuard;

    public AuthController(AuthService authService, GoogleAuthService googleAuthService, OtpService otpService,
                          RefreshCookie refreshCookie, OriginGuard originGuard) {
        this.authService = authService;
        this.googleAuthService = googleAuthService;
        this.otpService = otpService;
        this.refreshCookie = refreshCookie;
        this.originGuard = originGuard;
    }

    @Operation(summary = "Register a new user", description = "Creates an account (Donor, Requester, Hospital or Blood bank) and signs it in. Requires acceptTerms=true and a phone number verified by OTP.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterDto registerDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (registerDto.getPreferredLanguage() == null) {
            registerDto.setPreferredLanguage(AuthMessages.languageOf(request.getLocale()));
        }
        AuthResponse authResponse = authService.register(registerDto, ClientIp.of(request));
        refreshCookie.set(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @Operation(summary = "User login", description = "Authenticates by email or phone + password and issues access & refresh tokens. After repeated failures the account is locked for a while (429 auth.account.locked).")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginDto loginDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(loginDto, ClientIp.of(request));
        refreshCookie.set(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @Operation(summary = "Send OTP", description = "Sends a 6-digit code by SMS to a Sri Lankan mobile number (any common spelling; stored as +94...).")
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody OtpRequestDto otpRequestDto, HttpServletRequest request) {
        otpService.generateAndSendOtp(otpRequestDto.getPhoneNumber(), request.getLocale());
        return ResponseEntity.ok(ApiResponse.success(null, AuthCodes.OTP_SENT));
    }

    @Operation(summary = "Verify OTP", description = "Checks the code for the phone number; the phone then counts as verified for 30 minutes.")
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@Valid @RequestBody OtpVerifyDto otpVerifyDto) {
        if (!otpService.verifyOtp(otpVerifyDto.getPhoneNumber(), otpVerifyDto.getOtpCode())) {
            throw AuthException.badRequest(AuthCodes.OTP_INVALID);
        }
        return ResponseEntity.ok(ApiResponse.success(null, AuthCodes.OTP_VERIFIED));
    }

    @Operation(summary = "Reset password by phone", description = "Sets a new password for the account behind a phone number verified via OTP and signs out all devices.")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordDto dto, HttpServletRequest request) {
        authService.resetPassword(dto, ClientIp.of(request));
        return ResponseEntity.ok(ApiResponse.success(null, AuthCodes.PASSWORD_UPDATED));
    }

    @Operation(summary = "Request a password-reset email", description = "Always answers 200, whether or not the address has an account. The emailed link is single-use and valid for 30 minutes.")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordDto dto, HttpServletRequest request) {
        authService.forgotPassword(dto.getEmail(), ClientIp.of(request));
        return ResponseEntity.ok(ApiResponse.success(null, AuthCodes.RESET_EMAIL_REQUESTED));
    }

    @Operation(summary = "Reset password with the emailed token", description = "Sets a new password, signs out all devices.")
    @PostMapping("/reset-password/token")
    public ResponseEntity<ApiResponse<Void>> resetPasswordWithToken(@Valid @RequestBody ResetPasswordTokenDto dto, HttpServletRequest request) {
        authService.resetPasswordWithToken(dto, ClientIp.of(request));
        return ResponseEntity.ok(ApiResponse.success(null, AuthCodes.PASSWORD_UPDATED));
    }

    @Operation(summary = "Confirm an email address", description = "Spends the single-use token from the verification email.")
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailDto dto, HttpServletRequest request) {
        authService.verifyEmail(dto.getToken(), ClientIp.of(request));
        return ResponseEntity.ok(ApiResponse.success(null, AuthCodes.EMAIL_VERIFIED));
    }

    @Operation(summary = "Resend the verification email", description = "Signed-in users only; at most once a minute (429 auth.emailVerification.cooldown).")
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@AuthenticationPrincipal UserDetailsImpl principal) {
        if (principal == null) {
            throw AuthException.unauthorized(AuthCodes.NOT_SIGNED_IN);
        }
        authService.resendEmailVerification(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, AuthCodes.EMAIL_VERIFICATION_SENT));
    }

    @Operation(summary = "Accept an administrator invitation", description = "Creates the ADMIN account for an emailed invitation token. The phone must be verified by OTP first.")
    @PostMapping("/accept-invite")
    public ResponseEntity<ApiResponse<AuthResponse>> acceptInvite(
            @Valid @RequestBody AcceptInviteDto dto,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (dto.getPreferredLanguage() == null) {
            dto.setPreferredLanguage(AuthMessages.languageOf(request.getLocale()));
        }
        AuthResponse authResponse = authService.acceptInvite(dto, ClientIp.of(request));
        refreshCookie.set(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @Operation(summary = "Refresh access token", description = "Exchanges the HttpOnly refresh cookie for a new access token (and a new cookie). Reusing an already-rotated cookie revokes every session of the user.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        originGuard.check(request);
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw AuthException.unauthorized(AuthCodes.NOT_SIGNED_IN);
        }

        AuthResponse authResponse = authService.refreshToken(refreshToken, ClientIp.of(request));
        refreshCookie.set(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @Operation(summary = "Logout user", description = "Ends the session. With a valid access token every device is signed out; otherwise the session behind the cookie is ended. Always clears the cookie.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        originGuard.check(request);
        // Public on purpose so an expired session can still sign out.
        authService.logout(userDetails != null ? userDetails.getId() : null, refreshToken, ClientIp.of(request));
        refreshCookie.clear(response);
        return ResponseEntity.ok(ApiResponse.success(null, AuthCodes.LOGGED_OUT));
    }

    // ---- Google ----------------------------------------------------------------------------------------------

    @Operation(summary = "Verify Google User", description = "Verifies a Google ID token. Returns tokens for a known user; requiresRegistration for a first visit; requiresLinking when the email belongs to a verified password account (then call /google/link).")
    @PostMapping("/google/verify")
    public ResponseEntity<ApiResponse<GoogleVerifyResponse>> verifyGoogleUser(
            @Valid @RequestBody GoogleVerifyRequest requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        GoogleVerifyResponse result = googleAuthService.verify(requestDto, ClientIp.of(request));
        if (result.getAuthResponse() != null) {
            refreshCookie.set(response, result.getAuthResponse().getRefreshToken());
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Link Google to a password account", description = "Proves control of both: Google ID token + the account password.")
    @PostMapping("/google/link")
    public ResponseEntity<ApiResponse<AuthResponse>> linkGoogle(
            @Valid @RequestBody GoogleLinkRequest requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = googleAuthService.link(requestDto, ClientIp.of(request));
        refreshCookie.set(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @Operation(summary = "Register Google User", description = "Second step for a first-time Google user: role, name, phone (verified by OTP) and acceptTerms.")
    @PostMapping("/google/register")
    public ResponseEntity<ApiResponse<AuthResponse>> registerGoogleUser(
            @Valid @RequestBody GoogleRegisterRequest requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (requestDto.getPreferredLanguage() == null) {
            requestDto.setPreferredLanguage(AuthMessages.languageOf(request.getLocale()));
        }
        AuthResponse authResponse = googleAuthService.register(requestDto, ClientIp.of(request));
        refreshCookie.set(response, authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }
}
