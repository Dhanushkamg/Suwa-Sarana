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

        initAccount("admin@test.com", "0777654321", "password123", Role.ADMIN, VerificationStatus.VERIFIED);
        initAccount("hospital@test.com", "0771234567", "password123", Role.HOSPITAL_REQUESTER, VerificationStatus.VERIFIED);
        initAccount("donor@test.com", "0773334455", "password123", Role.DONOR, VerificationStatus.VERIFIED);
        initAccount("donor2@test.com", "0774445566", "password123", Role.DONOR, VerificationStatus.VERIFIED);
        initAccount("donor3@test.com", "0775556677", "password123", Role.DONOR, VerificationStatus.VERIFIED);
        initAccount("requester@test.com", "0772223344", "password123", Role.REQUESTER, VerificationStatus.VERIFIED);
        initAccount("pending_hospital@test.com", "0779998877", "password123", Role.HOSPITAL_REQUESTER, VerificationStatus.PENDING);
        initAccount("unverified@test.com", "0761234567", "password123", Role.REQUESTER, VerificationStatus.PENDING);

        initDonorProfiles();
        initActiveRequests();
        initReports();
        initCamps();
        initInventory();
    }

    private void initDonorProfiles() {
        try {
            userRepository.findByEmail("donor@test.com").ifPresent(u -> {
                jdbcTemplate.update(
                    "INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude, is_available, reliability_score, total_donations, lives_helped_estimate) " +
                    "SELECT ?, 'O+', '1995-04-12', 68.50, 'Colombo', 6.9271, 79.8612, TRUE, 85.00, 4, 12 " +
                    "WHERE NOT EXISTS (SELECT 1 FROM donor_profiles WHERE user_id = ?)",
                    u.getId(), u.getId()
                );
            });
            userRepository.findByEmail("donor2@test.com").ifPresent(u -> {
                jdbcTemplate.update(
                    "INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude, is_available, reliability_score, total_donations, lives_helped_estimate) " +
                    "SELECT ?, 'A-', '1998-08-20', 74.00, 'Kandy', 7.2906, 80.6337, TRUE, 92.00, 6, 18 " +
                    "WHERE NOT EXISTS (SELECT 1 FROM donor_profiles WHERE user_id = ?)",
                    u.getId(), u.getId()
                );
            });
            userRepository.findByEmail("donor3@test.com").ifPresent(u -> {
                jdbcTemplate.update(
                    "INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude, is_available, reliability_score, total_donations, lives_helped_estimate) " +
                    "SELECT ?, 'B+', '2000-01-15', 62.00, 'Galle', 6.0535, 80.2210, TRUE, 75.00, 2, 6 " +
                    "WHERE NOT EXISTS (SELECT 1 FROM donor_profiles WHERE user_id = ?)",
                    u.getId(), u.getId()
                );
            });
            log.info("Donor profiles initialized");
        } catch (Exception e) {
            log.warn("Could not init donor profiles: {}", e.getMessage());
        }
    }

    private void initActiveRequests() {
        try {
            Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM blood_requests WHERE status IN ('OPEN', 'ESCALATING', 'MATCHED') AND expires_at > now()",
                Integer.class
            );

            if (activeCount == null || activeCount < 3) {
                User hospital = userRepository.findByEmail("hospital@test.com").orElse(null);
                User requester = userRepository.findByEmail("requester@test.com").orElse(null);

                if (hospital != null) {
                    jdbcTemplate.update(
                        "INSERT INTO blood_requests (requester_id, patient_blood_type, units_needed, urgency, hospital_name, district, latitude, longitude, status, current_radius_km, created_at, expires_at, fraud_risk_score, ai_flag_reason) " +
                        "VALUES (?, 'O+', 2, 'CRITICAL', 'National Hospital of Sri Lanka', 'Colombo', 6.9271, 79.8612, 'OPEN', 10, now(), now() + INTERVAL '3 days', 5, 'Urgent postpartum hemorrhage indicated')",
                        hospital.getId()
                    );
                    jdbcTemplate.update(
                        "INSERT INTO blood_requests (requester_id, patient_blood_type, units_needed, urgency, hospital_name, district, latitude, longitude, status, current_radius_km, created_at, expires_at, fraud_risk_score, ai_flag_reason) " +
                        "VALUES (?, 'B+', 1, 'ROUTINE', 'Karapitiya Teaching Hospital', 'Galle', 6.0535, 80.2210, 'OPEN', 5, now(), now() + INTERVAL '5 days', 0, 'Routine orthopedic stabilization')",
                        hospital.getId()
                    );
                }
                if (requester != null) {
                    jdbcTemplate.update(
                        "INSERT INTO blood_requests (requester_id, patient_blood_type, units_needed, urgency, hospital_name, district, latitude, longitude, status, current_radius_km, created_at, expires_at, fraud_risk_score, ai_flag_reason) " +
                        "VALUES (?, 'A-', 3, 'URGENT', 'Teaching Hospital Kandy', 'Kandy', 7.2906, 80.6337, 'OPEN', 5, now(), now() + INTERVAL '2 days', 10, 'Cardiovascular bypass surgery scheduled')",
                        requester.getId()
                    );
                    jdbcTemplate.update(
                        "INSERT INTO blood_requests (requester_id, patient_blood_type, units_needed, urgency, hospital_name, district, latitude, longitude, status, current_radius_km, created_at, expires_at, fraud_risk_score, ai_flag_reason) " +
                        "VALUES (?, 'AB-', 8, 'CRITICAL', 'Teaching Hospital Jaffna', 'Jaffna', 9.6615, 80.0255, 'OPEN', 25, now(), now() + INTERVAL '1 day', 85, 'Unusually high unit count requested by unverified user')",
                        requester.getId()
                    );
                }
                log.info("Active blood requests initialized");
            }
        } catch (Exception e) {
            log.warn("Could not init active requests: {}", e.getMessage());
        }
    }

    private void initReports() {
        try {
            Integer reportCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM request_reports", Integer.class);
            if (reportCount == null || reportCount == 0) {
                jdbcTemplate.update(
                    "INSERT INTO request_reports (request_id, reported_by, reason) " +
                    "SELECT r.id, u.id, 'Suspected commercial blood sale solicitation or unverified high unit count' " +
                    "FROM blood_requests r, users u " +
                    "WHERE r.patient_blood_type = 'AB-' AND u.email = 'donor@test.com' LIMIT 1"
                );
                log.info("Abuse reports initialized");
            }
        } catch (Exception e) {
            log.warn("Could not init reports: {}", e.getMessage());
        }
    }

    private void initCamps() {
        try {
            Integer campCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM donation_camps", Integer.class);
            if (campCount == null || campCount == 0) {
                User hospital = userRepository.findByEmail("hospital@test.com").orElse(null);
                if (hospital != null) {
                    jdbcTemplate.update(
                        "INSERT INTO donation_camps (name, district, location, latitude, longitude, scheduled_date, start_time, end_time, organizer_id, status, created_at, updated_at) " +
                        "VALUES ('Colombo Red Cross Blood Drive', 'Colombo', 'Town Hall Grounds, Colombo 07', 6.9147, 79.8640, CURRENT_DATE + INTERVAL '2 days', '09:00:00', '15:00:00', ?, 'SCHEDULED', now(), now())",
                        hospital.getId()
                    );
                    jdbcTemplate.update(
                        "INSERT INTO donation_camps (name, district, location, latitude, longitude, scheduled_date, start_time, end_time, organizer_id, status, created_at, updated_at) " +
                        "VALUES ('Kandy Central Community Blood Camp', 'Kandy', 'Kandy City Centre Auditorium', 7.2936, 80.6366, CURRENT_DATE + INTERVAL '5 days', '08:30:00', '14:30:00', ?, 'SCHEDULED', now(), now())",
                        hospital.getId()
                    );
                    jdbcTemplate.update(
                        "INSERT INTO donation_camps (name, district, location, latitude, longitude, scheduled_date, start_time, end_time, organizer_id, status, created_at, updated_at) " +
                        "VALUES ('Southern Life Blood Donation Drive', 'Galle', 'Galle Fort Community Center', 6.0329, 80.2168, CURRENT_DATE + INTERVAL '12 days', '09:00:00', '16:00:00', ?, 'SCHEDULED', now(), now())",
                        hospital.getId()
                    );
                }
                log.info("Donation camps initialized");
            }
        } catch (Exception e) {
            log.warn("Could not init camps: {}", e.getMessage());
        }
    }

    private void initInventory() {
        try {
            Integer invCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM blood_bank_inventory", Integer.class);
            if (invCount == null || invCount == 0) {
                jdbcTemplate.update("INSERT INTO blood_bank_inventory (hospital_name, district, blood_type, status, updated_at) VALUES ('Batticaloa General Hospital', 'Batticaloa', 'O+', 'LOW', now())");
                jdbcTemplate.update("INSERT INTO blood_bank_inventory (hospital_name, district, blood_type, status, updated_at) VALUES ('Anuradhapura Teaching Hospital', 'Anuradhapura', 'A+', 'LOW', now())");
                jdbcTemplate.update("INSERT INTO blood_bank_inventory (hospital_name, district, blood_type, status, updated_at) VALUES ('Kurunegala Teaching Hospital', 'Kurunegala', 'O-', 'CRITICAL', now())");
                jdbcTemplate.update("INSERT INTO blood_bank_inventory (hospital_name, district, blood_type, status, updated_at) VALUES ('Badulla Provincial General Hospital', 'Badulla', 'B-', 'LOW', now())");
                log.info("Blood bank inventory initialized");
            }
        } catch (Exception e) {
            log.warn("Could not init inventory: {}", e.getMessage());
        }
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
