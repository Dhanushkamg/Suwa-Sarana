package com.suwasarana.api.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Placeholder implementation for Phase 2.
        // Will be replaced with DB lookup in Phase 3.
        if ("test@suwasarana.com".equals(username)) {
            return new UserDetailsImpl(1L, username, "mocked_hash", "REQUESTER");
        }
        throw new UsernameNotFoundException("User not found: " + username);
    }
}
