package com.suwasarana.api.matching;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscalationScheduler.class);

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private EscalationProcessor escalationProcessor;

    @Autowired
    private MatchTimeoutProcessor matchTimeoutProcessor;

    // Run every 5 minutes. Not transactional: each request is processed in its own transaction.
    @Scheduled(cron = "0 0/5 * * * *")
    public void processEscalations() {
        log.info("Running escalation scheduler...");

        // OPEN, ESCALATING and MATCHED: matched requests still have to expire or re-open
        List<BloodRequest> liveRequests = requestRepository.findLiveRequests();

        for (BloodRequest request : liveRequests) {
            try {
                escalationProcessor.processRequest(request.getId());
            } catch (Exception e) {
                log.error("Escalation failed for Request {}", request.getId(), e);
            }
        }

        try {
            matchTimeoutProcessor.markTimedOutMatches();
        } catch (Exception e) {
            log.error("Match response-timeout job failed", e);
        }
    }
}
