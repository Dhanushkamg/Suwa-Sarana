package com.suwasarana.api.scheduler;

import com.suwasarana.api.auth.otp.OtpVerificationRepository;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);
    private final RequestRepository requestRepository;
    private final OtpVerificationRepository otpRepository;

    public DataRetentionScheduler(RequestRepository requestRepository, OtpVerificationRepository otpRepository) {
        this.requestRepository = requestRepository;
        this.otpRepository = otpRepository;
    }

    /**
     * Purges EXPIRED or CANCELLED blood requests older than 30 days.
     * Fulfilled/matched requests are kept so donation history is not lost.
     * Runs every day at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredRequests() {
        log.info("Running DataRetentionScheduler to purge old data...");
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        int deletedCount = requestRepository.deleteByStatusInAndExpiresAtBefore(
                List.of(RequestStatus.EXPIRED, RequestStatus.CANCELLED), cutoffDate);
        log.info("Purged {} expired/cancelled blood requests older than 30 days.", deletedCount);
    }

    /** Phone numbers and codes from OTP checks are not kept beyond a day. Runs hourly. */
    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void purgeOldOtpVerifications() {
        otpRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(1));
    }
}
