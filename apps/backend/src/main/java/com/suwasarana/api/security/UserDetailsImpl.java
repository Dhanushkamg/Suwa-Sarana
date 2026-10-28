package com.suwasarana.api.security;

import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;

/**
 * The authenticated principal. {@code isEnabled()} is false for suspended/erased accounts (they cannot sign in and
 * their tokens stop working); {@code isAccountNonLocked()} is false while a brute-force lockout is running.
 */
public class UserDetailsImpl implements UserDetails {

    private final Long id;
    private final String username; // email
    private final String password;
    private final String role;
    private final Collection<? extends GrantedAuthority> authorities;
    private final int tokenVersion;
    private final boolean enabled;
    private final boolean accountNonLocked;

    public UserDetailsImpl(Long id, String username, String password, String role) {
        this(id, username, password, role, 0, true, true);
    }

    public UserDetailsImpl(Long id, String username, String password, String role, int tokenVersion,
                           boolean enabled, boolean accountNonLocked) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
        this.tokenVersion = tokenVersion;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
    }

    public static UserDetailsImpl build(User user) {
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash() != null ? user.getPasswordHash() : "",
                user.getRole().name(),
                user.getTokenVersion(),
                user.getStatus() == UserStatus.ACTIVE,
                !locked
        );
    }

    public Long getId() {
        return id;
    }

    public String getRoleName() {
        return role;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return enabled; }
}
