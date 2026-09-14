package com.suwasarana.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiApplication {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixDbConstraints() {
        try {
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
            jdbcTemplate.execute("ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('DONOR', 'REQUESTER', 'HOSPITAL_REQUESTER', 'BLOOD_BANK_REQUESTER', 'ADMIN'))");
            System.out.println("✅ Database constraint 'users_role_check' successfully updated for BLOOD_BANK_REQUESTER.");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to update database constraint: " + e.getMessage());
        }
    }

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
