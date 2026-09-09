package com.suwasarana.api.auth;

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

        AuthResponse response = authService.login(loginDto);

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

        assertThrows(RuntimeException.class, () -> authService.register(registerDto));
        verify(userRepository, never()).save(any());
    }

    @Test
    void refreshToken_validToken_shouldReturnNewTokens() {
        String rawToken = "valid-refresh-token";
        String tokenHash = org.springframework.util.DigestUtils.md5DigestAsHex(rawToken.getBytes());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(sampleUser);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(java.time.Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(refreshToken));
        when(tokenProvider.generateTokenFromUsername("donor@test.com")).thenReturn("new-jwt-token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refreshToken(rawToken);

        assertNotNull(response);
        assertEquals("new-jwt-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());
    }
}
