package com.suwasarana.api.notification;

import com.suwasarana.api.circle.PledgeNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tells a requester, through their inbox (and live stream), that someone pledged through their private circle.
 * Returns true only when the inbox row was written, so the circle page can honestly say "the requester has been notified".
 * No phone number or other contact data is carried: the requester reads those in the circle panel.
 */
@Component
public class InboxPledgeNotifier implements PledgeNotifier {

    private static final Logger log = LoggerFactory.getLogger(InboxPledgeNotifier.class);

    private final NotificationInboxService inbox;

    @Autowired
    public InboxPledgeNotifier(NotificationInboxService inbox) {
        this.inbox = inbox;
    }

    @Override
    public boolean pledgeReceived(long requestId, long requesterUserId, String volunteerName, String bloodType, int pledgeCount) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("requestId", requestId);
            params.put("volunteerName", volunteerName);
            params.put("bloodType", bloodType);
            params.put("pledgeCount", pledgeCount);
            inbox.add(requesterUserId, "CIRCLE_PLEDGE", "notifications.pledge.title", "notifications.pledge.body", params, requestId);
            return true;
        } catch (Exception e) {
            log.warn("Could not store the circle pledge notification for request {} ({})", requestId, e.getClass().getSimpleName());
            return false;
        }
    }
}
