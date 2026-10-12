package com.suwasarana.api.auth;

import com.suwasarana.api.audit.AuditLogService;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.LoginDto;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.security.JwtTokenProvider;
import com.suwasarana.api.security.UserDetailsImpl;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private com.suwasarana.api.auth.otp.OtpService otpService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setEmail("donor@test.com");
        sampleUser.setPhoneNumber("0712345678");
        sampleUser.setRole(Role.DONOR);
    }

    @Test
    void login_validCredentials_shouldReturnAuthResponse() {
        LoginDto loginDto = new LoginDto();
        loginDto.setUsername("donor@test.com");
        loginDto.setPassword("password123");

        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        when(userDetails.getId()).thenReturn(1L);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        when(tokenProvider.generateToken(authentication)).thenReturn("mock-jwt-token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(loginDto, "127.0.0.1");

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getAccessToken());
        assertEquals(Role.DONOR, response.getRole());
        assertNotNull(response.getRefreshToken());
    }

    @Test
    void register_existingEmail_shouldThrowException() {
        RegisterDto registerDto = new RegisterDto();
        registerDto.setEmail("existing@test.com");
        registerDto.setPhoneNumber("0712345678");
        registerDto.setPassword("pass123");
        registerDto.setRole(Role.DONOR);

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> authService.register(registerDto, "127.0.0.1"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_adminRole_isRejectedBeforeAnythingIsSaved() {
        RegisterDto registerDto = new RegisterDto();
        registerDto.setEmail("evil@test.com");
        registerDto.setPhoneNumber("0712345678");
        registerDto.setPassword("password123");
        registerDto.setRole(Role.ADMIN);

        com.suwasarana.api.common.ApiException ex = assertThrows(com.suwasarana.api.common.ApiException.class,
                () -> authService.register(registerDto, "127.0.0.1"));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_unverifiedPhone_isRejected() {
        RegisterDto registerDto = new RegisterDto();
        registerDto.setEmail("new@test.com");
        registerDto.setPhoneNumber("0712345678");
        registerDto.setPassword("password123");
        registerDto.setRole(Role.DONOR);
        when(otpService.isPhoneVerified("0712345678")).thenReturn(false);

        assertThrows(com.suwasarana.api.common.ApiException.class, () -> authService.register(registerDto, "127.0.0.1"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_verifiedPhone_updatesPasswordAndSignsOutEverywhere() {
        com.suwasarana.api.auth.dto.ResetPasswordDto dto = new com.suwasarana.api.auth.dto.ResetPasswordDto();
        dto.setPhoneNumber("+94712345678");
        dto.setNewPassword("brand-new-pass");
        when(otpService.isPhoneVerified("+94712345678")).thenReturn(true);
        when(userRepository.findByPhoneNumber("+94712345678")).thenReturn(Optional.empty());
        // Stored in the older 07XXXXXXXX form
        when(userRepository.findByPhoneNumber("0712345678")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.encode("brand-new-pass")).thenReturn("hashed");

        authService.resetPassword(dto, "127.0.0.1");

        assertEquals("hashed", sampleUser.getPasswordHash());
        verify(refreshTokenRepository).deleteByUser(sampleUser);
        verify(otpService).consume("+94712345678");
    }

    @Test
    void resetPassword_unverifiedPhone_isRejected() {
        com.suwasarana.api.auth.dto.ResetPasswordDto dto = new com.suwasarana.api.auth.dto.ResetPasswordDto();
        dto.setPhoneNumber("+94712345678");
        dto.setNewPassword("brand-new-pass");
        when(otpService.isPhoneVerified("+94712345678")).thenReturn(false);

        assertThrows(com.suwasarana.api.common.ApiException.class, () -> authService.resetPassword(dto, "127.0.0.1"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void refreshToken_unknownToken_isUnauthorized() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        com.suwasarana.api.common.ApiException ex = assertThrows(com.suwasarana.api.common.ApiException.class,
                () -> authService.refreshToken("nope", "127.0.0.1"));

        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void refreshToken_validToken_shouldReturnNewTokens() {
        String rawToken = "valid-refresh-token";
        String tokenHash = AuthService.sha256Hex(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(sampleUser);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(java.time.Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
        when(tokenProvider.generateTokenFromUsername("donor@test.com")).thenReturn("new-jwt-token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refreshToken(rawToken, "127.0.0.1");

        assertNotNull(response);
        assertEquals("new-jwt-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
    }
}
