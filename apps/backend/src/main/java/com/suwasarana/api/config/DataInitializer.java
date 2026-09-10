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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) DEFAULT 'PENDING'");
            jdbcTemplate.execute("UPDATE users SET verification_status = 'VERIFIED' WHERE is_verified = TRUE AND (verification_status IS NULL OR verification_status = 'PENDING')");
            jdbcTemplate.execute("ALTER TABLE blood_requests ADD COLUMN IF NOT EXISTS fraud_risk_score INTEGER");
            jdbcTemplate.execute("ALTER TABLE blood_requests ADD COLUMN IF NOT EXISTS ai_flag_reason VARCHAR(255)");
            jdbcTemplate.execute("ALTER TABLE blood_requests ADD COLUMN IF NOT EXISTS triaged_at TIMESTAMPTZ");
        } catch (Exception e) {
            log.info("Schema guard check completed: {}", e.getMessage());
        }

        initAccount("admin@test.com", "0777654321", "AdminPass1", Role.ADMIN, VerificationStatus.VERIFIED);
        initAccount("hospital@test.com", "0771234567", "AdminPass1", Role.HOSPITAL_REQUESTER, VerificationStatus.VERIFIED);
        initAccount("donor@test.com", "0712345678", "AdminPass1", Role.DONOR, VerificationStatus.VERIFIED);
        initAccount("requester@test.com", "0781234567", "AdminPass1", Role.REQUESTER, VerificationStatus.VERIFIED);
        initAccount("unverified@test.com", "0761234567", "AdminPass1", Role.REQUESTER, VerificationStatus.PENDING);
    }

    private void initAccount(String email, String phone, String password, Role role, VerificationStatus status) {
        try {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                user = new User();
                user.setEmail(email);
                user.setPhoneNumber(phone);
            }
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(role);
            user.setVerificationStatus(status);
            userRepository.save(user);
            log.info("Initialized test account: {} with role: {} and status: {}", email, role, status);
        } catch (Exception e) {
            log.warn("Could not initialize account {}: {}", email, e.getMessage());
        }
    }
}
