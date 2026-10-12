package com.suwasarana.api.auth.otp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    void deleteByPhoneNumber(String phoneNumber);

    void deleteByCreatedAtBefore(java.time.LocalDateTime cutoff);
}
