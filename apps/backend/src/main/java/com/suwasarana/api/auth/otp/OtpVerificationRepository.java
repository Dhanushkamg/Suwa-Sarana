package com.suwasarana.api.auth.otp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    /** Latest record under any accepted spelling of the number (E.164 and the legacy 07XXXXXXXX form). */
    Optional<OtpVerification> findTopByPhoneNumberInOrderByCreatedAtDesc(Collection<String> phoneNumbers);

    void deleteByPhoneNumber(String phoneNumber);

    void deleteByPhoneNumberIn(Collection<String> phoneNumbers);

    void deleteByCreatedAtBefore(java.time.LocalDateTime cutoff);
}
