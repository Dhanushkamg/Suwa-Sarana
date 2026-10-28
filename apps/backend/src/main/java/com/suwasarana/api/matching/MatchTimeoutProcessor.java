package com.suwasarana.api.matching;

import com.suwasarana.api.shared.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A donor who was alerted but never answered within the response window is marked NO_RESPONSE. This frees the
 * slot in the analytics ("pending" donors) and tells the requester who has really gone quiet. It does not touch the
 * donor's reliability score: not seeing an alert is not the same as a no-show after accepting.
 * NO_RESPONSE donors are not re-alerted for the same request (unique request/donor pair).
 */
@Service
public class MatchTimeoutProcessor {

    private static final Logger log = LoggerFactory.getLogger(MatchTimeoutProcessor.class);

    @Autowired
    private RequestMatchRepository matchRepository;

    @Autowired
    private AppClock clock;

    @Value("${app.matching.response-timeout-minutes:30}")
    private long responseTimeoutMinutes = 30;

    @Transactional
    public int markTimedOutMatches() {
        LocalDateTime cutoff = clock.utcNow().minusMinutes(responseTimeoutMinutes);
        List<RequestMatch> stale = matchRepository.findByStatusAndCreatedAtBefore(MatchStatus.PENDING, cutoff);
        for (RequestMatch m : stale) {
            m.setStatus(MatchStatus.NO_RESPONSE);
        }
        matchRepository.saveAll(stale);
        if (!stale.isEmpty()) {
            log.info("Marked {} pending matches NO_RESPONSE (no answer within {} min)", stale.size(), responseTimeoutMinutes);
        }
        return stale.size();
    }
}
