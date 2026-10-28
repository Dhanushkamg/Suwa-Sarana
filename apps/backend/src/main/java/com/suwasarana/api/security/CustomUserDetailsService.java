package com.suwasarana.api.security;

import com.suwasarana.api.user.PhoneNumbers;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return UserDetailsImpl.build(findUser(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found")));
    }

    /** An account is found by email (case-insensitive) or by phone number in any accepted spelling. */
    public Optional<User> findUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String id = identifier.trim();
        if (id.contains("@")) {
            return userRepository.findByEmailIgnoreCase(id);
        }
        return userRepository.findFirstByPhoneNumberIn(PhoneNumbers.variants(id));
    }
}
