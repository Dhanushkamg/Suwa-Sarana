package com.suwasarana.api.scheduler;

import com.suwasarana.api.request.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);
    private final RequestRepository requestRepository;

    public DataRetentionScheduler(RequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    /**
     * Purges expired blood requests older than 30 days.
     * Runs every day at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredRequests() {
        log.info("Running DataRetentionScheduler to purge old data...");
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        int deletedCount = requestRepository.deleteByExpiresAtBefore(cutoffDate);
        log.info("Purged {} expired blood requests older than 30 days.", deletedCount);
    }
}
