package com.suwasarana.api.notification;

import com.suwasarana.api.matching.DonorsMatchedEvent;
import com.suwasarana.api.matching.RequestMatch;
import com.suwasarana.api.matching.RequestMatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Alerts donors only AFTER the transaction that created the request and its matches has committed (audit F-16):
 * if the request creation rolls back, nobody is told about a request that does not exist. Each donor is handled in
 * its own try block so one failing SMS never stops the others.
 */
@Component
public class MatchNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(MatchNotificationListener.class);

    @Autowired
    private RequestMatchRepository matchRepository;

    @Autowired
    private NotificationCascadeService cascade;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDonorsMatched(DonorsMatchedEvent event) {
        for (RequestMatch match : matchRepository.findByIdIn(event.matchIds())) {
            try {
                cascade.sendNotification(match.getDonor(), match.getRequest());
            } catch (Exception e) {
                log.error("Alerting donor {} for request {} failed ({})", match.getDonor().getId(), event.requestId(), e.getClass().getSimpleName());
            }
        }
    }
}
