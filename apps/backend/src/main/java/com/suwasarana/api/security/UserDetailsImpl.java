package com.suwasarana.api.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class UserDetailsImpl implements UserDetails {

    private final Long id;
    private final String username; // email or phone
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id, String username, String password, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public static UserDetailsImpl build(com.suwasarana.api.user.User user) {
        return new UserDetailsImpl(
            user.getId(),
            user.getEmail(),
            user.getPasswordHash() != null ? user.getPasswordHash() : "",
            user.getRole().name()
        );
    }

    public Long getId() {
        return id;
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
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}
