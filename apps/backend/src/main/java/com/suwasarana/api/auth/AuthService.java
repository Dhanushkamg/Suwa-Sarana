package com.suwasarana.api.auth;

import com.suwasarana.api.audit.AuditLogService;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.LoginDto;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.security.JwtTokenProvider;
import com.suwasarana.api.security.UserDetailsImpl;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.NicValidator;
import com.suwasarana.api.auth.otp.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogService auditLog;

    @Autowired
    private OtpService otpService;

    public AuthResponse register(RegisterDto registerDto, String ipAddress) {
        validateNewAccount(registerDto.getEmail(), registerDto.getPhoneNumber(), registerDto.getNicNumber(), registerDto.getRole());

        User user = new User();
        user.setEmail(registerDto.getEmail());
        user.setFirstName(registerDto.getFirstName());
        user.setLastName(registerDto.getLastName());
        user.setPhoneNumber(registerDto.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(registerDto.getPassword()));
        user.setRole(registerDto.getRole());
        user.setNicNumber(registerDto.getNicNumber());

        User savedUser = userRepository.save(user);

        // Auto login after registration
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(registerDto.getEmail(), registerDto.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);
        String refreshToken = createRefreshToken(savedUser);

        otpService.consume(savedUser.getPhoneNumber());
        auditLog.logRegistration(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name(), ipAddress);

        return new AuthResponse(jwt, savedUser.getId(), savedUser.getRole(), savedUser.getEmail(), savedUser.getPhoneNumber(), refreshToken, savedUser.getVerificationStatus());
    }

    /** Shared checks for password and Google sign-ups. */
    private void validateNewAccount(String email, String phoneNumber, String nicNumber, Role role) {
        // Privileged accounts are provisioned by operators, never through public sign-up.
        if (role == Role.ADMIN) {
            throw ApiException.badRequest("This account type cannot be created through registration.");
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email is already in use!");
        }
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw ApiException.conflict("Phone number is already in use!");
        }
        if (nicNumber != null && !nicNumber.isBlank() && !NicValidator.isValid(nicNumber)) {
            throw ApiException.badRequest("Invalid NIC format.");
        }
        if (!otpService.isPhoneVerified(phoneNumber)) {
            throw ApiException.badRequest("Phone number is not verified! Please complete OTP verification.");
        }
    }

    public AuthResponse login(LoginDto loginDto, String ipAddress) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = tokenProvider.generateToken(authentication);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            User user = userRepository.findById(userDetails.getId()).orElseThrow();

            String refreshToken = createRefreshToken(user);

            auditLog.logLoginSuccess(user.getId(), user.getEmail(), ipAddress);

            return new AuthResponse(jwt, user.getId(), user.getRole(), user.getEmail(), user.getPhoneNumber(), refreshToken, user.getVerificationStatus());
        } catch (AuthenticationException ex) {
            auditLog.logLoginFailure(loginDto.getUsername(), ipAddress, "Bad credentials");
            throw ex;
        }
    }

    private String createRefreshToken(User user) {
        String tokenStr = java.util.UUID.randomUUID().toString();
        String tokenHash = sha256Hex(tokenStr);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofDays(7)));

        refreshTokenRepository.save(refreshToken);

        return tokenStr; // Return raw token to the caller — only the hash is stored
    }

    /**
     * Hashes a raw token string with SHA-256, returning a lowercase hex string.
     * MD5 is cryptographically broken and must NOT be used for security tokens.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec — this path is unreachable
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public AuthResponse refreshToken(String requestRefreshToken, String ipAddress) {
        String tokenHash = sha256Hex(requestRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> ApiException.unauthorized("Session expired. Please sign in again."));

        if (refreshToken.getExpiresAt().compareTo(java.time.Instant.now()) < 0) {
            refreshTokenRepository.delete(refreshToken);
            throw ApiException.unauthorized("Session expired. Please sign in again.");
        }

        User user = refreshToken.getUser();

        // Generate new Access Token
        String token = tokenProvider.generateTokenFromUsername(user.getEmail());

        // Rotate refresh token — old one is deleted, new one is issued
        refreshTokenRepository.delete(refreshToken);
        String newRefreshToken = createRefreshToken(user);

        auditLog.logTokenRefresh(user.getId(), ipAddress);

        return new AuthResponse(token, user.getId(), user.getRole(), user.getEmail(), user.getPhoneNumber(), newRefreshToken, user.getVerificationStatus());
    }

    /** Sets a new password for the account behind a phone number that has just passed OTP verification. */
    @org.springframework.transaction.annotation.Transactional
    public void resetPassword(com.suwasarana.api.auth.dto.ResetPasswordDto dto, String ipAddress) {
        String phone = dto.getPhoneNumber();
        if (!otpService.isPhoneVerified(phone)) {
            throw ApiException.badRequest("Phone number is not verified! Please complete OTP verification.");
        }

        // Numbers are stored either as 07XXXXXXXX or +947XXXXXXXX
        String alternate = phone.startsWith("+94") ? "0" + phone.substring(3) : phone.startsWith("0") ? "+94" + phone.substring(1) : phone;
        User user = userRepository.findByPhoneNumber(phone)
                .or(() -> userRepository.findByPhoneNumber(alternate))
                .orElseThrow(() -> ApiException.badRequest("Could not reset the password for this number."));

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user); // sign out every device
        otpService.consume(phone);
        auditLog.logPasswordReset(user.getId(), ipAddress);
    }

    @org.springframework.transaction.annotation.Transactional
    public void logout(Long userId, String ipAddress) {
        User user = userRepository.findById(userId).orElseThrow();
        refreshTokenRepository.deleteByUser(user);
        auditLog.logLogout(userId, ipAddress);
    }

    @org.springframework.beans.factory.annotation.Value("${app.google.client-id}")
    private String googleClientId;

    /** Verifies a Google ID token and returns its payload; throws 401 if the token or its email is not trustworthy. */
    private com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        com.google.api.client.googleapis.auth.oauth2.GoogleIdToken idToken;
        try {
            idToken = new com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder(
                    new com.google.api.client.http.javanet.NetHttpTransport(),
                    new com.google.api.client.json.gson.GsonFactory())
                .setAudience(java.util.Collections.singletonList(googleClientId))
                .build()
                .verify(idTokenString);
        } catch (java.security.GeneralSecurityException | java.io.IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Could not verify Google sign-in right now. Please try again.");
        }
        if (idToken == null || !Boolean.TRUE.equals(idToken.getPayload().getEmailVerified())) {
            throw ApiException.unauthorized("Invalid Google sign-in.");
        }
        return idToken.getPayload();
    }

    private AuthResponse issueTokens(User user, String ipAddress) {
        org.springframework.security.core.userdetails.UserDetails userDetails = UserDetailsImpl.build(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = tokenProvider.generateToken(authentication);
        String refreshToken = createRefreshToken(user);
        return new AuthResponse(jwt, user.getId(), user.getRole(), user.getEmail(), user.getPhoneNumber(), refreshToken, user.getVerificationStatus());
    }

    public com.suwasarana.api.auth.dto.GoogleVerifyResponse verifyGoogleUser(com.suwasarana.api.auth.dto.GoogleVerifyRequest request, String ipAddress) {
        String email = verifyGoogleToken(request.getIdToken()).getEmail();

        java.util.Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return new com.suwasarana.api.auth.dto.GoogleVerifyResponse(true, email, null);
        }

        User user = userOptional.get();
        AuthResponse authResponse = issueTokens(user, ipAddress);
        auditLog.logLoginSuccess(user.getId(), user.getEmail(), ipAddress);
        return new com.suwasarana.api.auth.dto.GoogleVerifyResponse(false, email, authResponse);
    }

    public AuthResponse registerGoogleUser(com.suwasarana.api.auth.dto.GoogleRegisterRequest request, String ipAddress) {
        com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload = verifyGoogleToken(request.getIdToken());
        String email = payload.getEmail();

        validateNewAccount(email, request.getPhoneNumber(), request.getNicNumber(), request.getRole());

        User user = new User();
        user.setEmail(email);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(request.getRole());
        user.setNicNumber(request.getNicNumber());
        user.setAuthProvider(com.suwasarana.api.user.AuthProvider.GOOGLE);
        user.setProviderId(payload.getSubject());
        User savedUser = userRepository.save(user);

        AuthResponse authResponse = issueTokens(savedUser, ipAddress);
        otpService.consume(savedUser.getPhoneNumber());
        auditLog.logRegistration(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name(), ipAddress);
        return authResponse;
    }
}
