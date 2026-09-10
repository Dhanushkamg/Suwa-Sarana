package com.suwasarana.api.auth;

import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.LoginDto;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.security.JwtTokenProvider;
import com.suwasarana.api.security.UserDetailsImpl;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public AuthResponse register(RegisterDto registerDto) {
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

        return new AuthResponse(jwt, savedUser.getId(), savedUser.getRole(), savedUser.getEmail(), savedUser.getPhoneNumber(), refreshToken);
    }

    public AuthResponse login(LoginDto loginDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDto.getUsername(), loginDto.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        
        String refreshToken = createRefreshToken(user);

        return new AuthResponse(jwt, user.getId(), user.getRole(), user.getEmail(), user.getPhoneNumber(), refreshToken);
    }

    private String createRefreshToken(User user) {
        String tokenStr = java.util.UUID.randomUUID().toString();
        // Simple hash (In production, use SHA-256)
        String tokenHash = org.springframework.util.DigestUtils.md5DigestAsHex(tokenStr.getBytes());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofDays(7)));
        
        refreshTokenRepository.save(refreshToken);
        
        return tokenStr; // Return raw token to the user
    }

    public AuthResponse refreshToken(String requestRefreshToken) {
        String tokenHash = org.springframework.util.DigestUtils.md5DigestAsHex(requestRefreshToken.getBytes());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));

        if (refreshToken.getExpiresAt().compareTo(java.time.Instant.now()) < 0) {
            refreshTokenRepository.delete(refreshToken);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }

        User user = refreshToken.getUser();
        
        // Generate new Access Token
        String token = tokenProvider.generateTokenFromUsername(user.getEmail());
        
        // Generate new Refresh Token (Rotation)
        refreshTokenRepository.delete(refreshToken);
        String newRefreshToken = createRefreshToken(user);
        
        return new AuthResponse(token, user.getId(), user.getRole(), user.getEmail(), user.getPhoneNumber(), newRefreshToken);
    }

    @org.springframework.transaction.annotation.Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        refreshTokenRepository.deleteByUser(user);
    }
}
