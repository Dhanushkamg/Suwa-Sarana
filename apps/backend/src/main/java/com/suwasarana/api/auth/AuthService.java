package com.suwasarana.api.auth;

import com.suwasarana.api.audit.AuditLogService;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.LoginDto;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.security.JwtTokenProvider;
import com.suwasarana.api.security.UserDetailsImpl;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
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

    public AuthResponse register(RegisterDto registerDto, String ipAddress) {
        if (userRepository.existsByEmail(registerDto.getEmail())) {
            throw new RuntimeException("Email is already in use!");
        }
        if (userRepository.existsByPhoneNumber(registerDto.getPhoneNumber())) {
            throw new RuntimeException("Phone number is already in use!");
        }

        User user = new User();
        user.setEmail(registerDto.getEmail());
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

        auditLog.logRegistration(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name(), ipAddress);

        return new AuthResponse(jwt, savedUser.getId(), savedUser.getRole(), savedUser.getEmail(), savedUser.getPhoneNumber(), refreshToken, savedUser.getVerificationStatus());
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
    static String sha256Hex(String input) {
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
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));

        if (refreshToken.getExpiresAt().compareTo(java.time.Instant.now()) < 0) {
            refreshTokenRepository.delete(refreshToken);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
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

    @org.springframework.transaction.annotation.Transactional
    public void logout(Long userId, String ipAddress) {
        User user = userRepository.findById(userId).orElseThrow();
        refreshTokenRepository.deleteByUser(user);
        auditLog.logLogout(userId, ipAddress);
    }
}
