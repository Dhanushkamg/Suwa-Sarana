package com.suwasarana.api.circle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback notifier: records the event in the log (no personal data) and reports that nobody was notified,
 * so the API does not claim otherwise. Replace / complement it with a bean that writes an in-app notification.
 */
@Component
public class LoggingPledgeNotifier implements PledgeNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPledgeNotifier.class);

    @Override
    public boolean pledgeReceived(long requestId, long requesterUserId, String volunteerName, String bloodType, int pledgeCount) {
        log.info("Circle pledge received for request {} (now {} pledge(s)); no requester notification channel is configured",
                requestId, pledgeCount);
        return false;
    }
}
