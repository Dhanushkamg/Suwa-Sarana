package com.suwasarana.api.config;

import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initAccount("admin@test.com", "0777654321", "AdminPass1", Role.ADMIN);
        initAccount("hospital@test.com", "0771234567", "AdminPass1", Role.HOSPITAL_REQUESTER);
        initAccount("donor@test.com", "0712345678", "AdminPass1", Role.DONOR);
    }

    private void initAccount(String email, String phone, String password, Role role) {
        try {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                user = new User();
                user.setEmail(email);
                user.setPhoneNumber(phone);
            }
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(role);
            user.setVerificationStatus(VerificationStatus.VERIFIED);
            userRepository.save(user);
            log.info("Initialized test account: {} with role: {}", email, role);
        } catch (Exception e) {
            log.warn("Could not initialize account {}: {}", email, e.getMessage());
        }
    }
}
