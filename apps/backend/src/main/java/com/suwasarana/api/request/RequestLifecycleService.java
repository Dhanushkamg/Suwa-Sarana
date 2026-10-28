package com.suwasarana.api.request;

import com.suwasarana.api.donor.DonationRecordRepository;
import com.suwasarana.api.matching.MatchStatus;
import com.suwasarana.api.matching.RequestMatch;
import com.suwasarana.api.matching.RequestMatchRepository;
import com.suwasarana.api.notification.NotificationInboxService;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.shared.CodedApiException;
import com.suwasarana.api.shared.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every status change of a blood request goes through here, so closed_at, matched_at, the requester and donor
 * notifications and the fulfilment-rate inputs are always consistent.
 *
 * <pre>
 *   OPEN/ESCALATING --(enough donors accepted)--> MATCHED --(confirmed donations >= units)--> FULFILLED
 *        |                                           |  \--(accepted donor did not confirm in time)--> ESCALATING
 *        |                                           \--(expiry reached)--> EXPIRED
 *        +--(owner closes / cancels)--> CANCELLED    +--(owner/admin marks fulfilled)--> FULFILLED
 *        +--(expiry reached)--> EXPIRED
 * </pre>
 * FULFILLED, CANCELLED and EXPIRED are terminal.
 */
@Service
public class RequestLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(RequestLifecycleService.class);

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private RequestMatchRepository matchRepository;

    @Autowired
    private DonationRecordRepository donationRecordRepository;

    @Autowired
    private NotificationInboxService inbox;

    @Autowired
    private AppClock clock;

    /** How long an accepted donor has to be confirmed (donated) before the request re-opens. */
    @Value("${app.requests.confirmation-window-hours:4}")
    private long confirmationWindowHours = 4;

    /** Minimum time left on a request that re-opens, so replacement donors have a chance to respond. */
    @Value("${app.requests.reopen-extension-minutes:120}")
    private long reopenExtensionMinutes = 120;

    // ---------------------------------------------------------------- owner actions

    /** Owner or admin states that the need has been met. */
    @Transactional
    public BloodRequest fulfil(Long requestId, Long actorId, boolean admin) {
        BloodRequest r = loadOwned(requestId, actorId, admin);
        assertActive(r);
        finish(r, RequestStatus.FULFILLED, null);
        notifyDonorsOfClosure(r);
        return r;
    }

    /** Owner or admin closes a request that is no longer needed (status CANCELLED, with a reason). */
    @Transactional
    public BloodRequest close(Long requestId, Long actorId, boolean admin, CloseReason reason) {
        BloodRequest r = loadOwned(requestId, actorId, admin);
        assertActive(r);
        finish(r, RequestStatus.CANCELLED, (reason == null ? CloseReason.NO_LONGER_NEEDED : reason).name());
        notifyDonorsOfClosure(r);
        return r;
    }

    // ---------------------------------------------------------------- driven by donor activity

    /** Called after a donor accepts: enough live acceptances means the request is MATCHED. */
    @Transactional
    public void onDonorAccepted(BloodRequest r) {
        if (r.getStatus() != RequestStatus.OPEN && r.getStatus() != RequestStatus.ESCALATING) return;
        long live = matchRepository.countLiveAccepted(r.getId());
        notifyRequester(r, "MATCH_ACCEPTED", "notifications.accepted.title", "notifications.accepted.body",
                Map.of("hospital", r.getHospitalName(), "accepted", live, "needed", (int) r.getUnitsNeeded()));
        if (live >= r.getUnitsNeeded()) {
            LocalDateTime now = clock.utcNow();
            r.setStatus(RequestStatus.MATCHED);
            r.setMatchedAt(now);
            // Give the accepted donors the confirmation window (+30 min) even if the request would expire sooner
            LocalDateTime needUntil = now.plusHours(confirmationWindowHours).plusMinutes(30);
            if (r.getExpiresAt().isBefore(needUntil)) r.setExpiresAt(needUntil);
            requestRepository.save(r);
            log.info("Request {} is MATCHED ({} of {} donors accepted)", r.getId(), live, r.getUnitsNeeded());
        }
    }

    /** Called after a donation is recorded: confirmed donations >= units means FULFILLED. */
    @Transactional
    public void onDonationRecorded(BloodRequest r) {
        if (!r.getStatus().isActive()) return;
        long donated = donationRecordRepository.countByRequestId(r.getId());
        if (donated >= r.getUnitsNeeded()) {
            finish(r, RequestStatus.FULFILLED, null);
            notifyRequester(r, "REQUEST_FULFILLED", "notifications.fulfilled.title", "notifications.fulfilled.body",
                    Map.of("hospital", r.getHospitalName()));
            notifyDonorsOfClosure(r);
        }
    }

    /** Called after a no-show is recorded: a MATCHED request that no longer has enough live donors re-opens. */
    @Transactional
    public void onDonorNoShow(BloodRequest r) {
        if (r.getStatus() != RequestStatus.MATCHED) return;
        long live = matchRepository.countLiveAccepted(r.getId());
        if (live < r.getUnitsNeeded()) {
            reopen(r);
        }
    }

    // ---------------------------------------------------------------- driven by the scheduler

    @Transactional
    public void expire(BloodRequest r) {
        if (!r.getStatus().isActive()) return;
        finish(r, RequestStatus.EXPIRED, null);
        notifyRequester(r, "REQUEST_EXPIRED", "notifications.expired.title", "notifications.expired.body",
                Map.of("hospital", r.getHospitalName()));
        log.info("Request {} expired", r.getId());
    }

    /**
     * A MATCHED request whose accepted donors were not confirmed within the window: those matches become
     * NO_RESPONSE (no reliability penalty, since the requester may simply not have confirmed) and the request
     * goes back to ESCALATING so more donors are found, with at least {@code reopenExtensionMinutes} left.
     */
    @Transactional
    public void lapseUnconfirmed(BloodRequest r) {
        List<RequestMatch> unconfirmed = matchRepository.findUnconfirmedAccepted(r.getId());
        for (RequestMatch m : unconfirmed) {
            m.setStatus(MatchStatus.NO_RESPONSE);
            matchRepository.save(m);
        }
        reopen(r);
    }

    private void reopen(BloodRequest r) {
        LocalDateTime now = clock.utcNow();
        r.setStatus(RequestStatus.ESCALATING);
        r.setMatchedAt(null);
        r.setLastEscalatedAt(now);
        LocalDateTime minExpiry = now.plusMinutes(reopenExtensionMinutes);
        if (r.getExpiresAt().isBefore(minExpiry)) r.setExpiresAt(minExpiry);
        requestRepository.save(r);
        notifyRequester(r, "REQUEST_REOPENED", "notifications.reopened.title", "notifications.reopened.body",
                Map.of("hospital", r.getHospitalName()));
        log.info("Request {} re-opened: accepted donor(s) did not confirm in time", r.getId());
    }

    /** True when a MATCHED request has been waiting longer than the confirmation window. */
    public boolean confirmationWindowElapsed(BloodRequest r) {
        LocalDateTime since = r.getMatchedAt();
        return since != null && since.plusHours(confirmationWindowHours).isBefore(clock.utcNow());
    }

    // ---------------------------------------------------------------- helpers

    private BloodRequest loadOwned(Long requestId, Long actorId, boolean admin) {
        BloodRequest r = requestRepository.findById(requestId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.REQUEST_NOT_FOUND, "Request not found"));
        if (!admin && !r.getRequester().getId().equals(actorId)) {
            throw CodedApiException.forbidden(ErrorCodes.REQUEST_NOT_OWNED, "You can only change your own requests");
        }
        return r;
    }

    private void assertActive(BloodRequest r) {
        if (!r.getStatus().isActive()) {
            throw CodedApiException.conflict(ErrorCodes.REQUEST_ALREADY_CLOSED,
                    "This request is already " + r.getStatus().name().toLowerCase() + ".");
        }
    }

    private void finish(BloodRequest r, RequestStatus status, String closeReason) {
        r.setStatus(status);
        r.setClosedAt(clock.utcNow());
        r.setCloseReason(closeReason);
        requestRepository.save(r);
    }

    private void notifyRequester(BloodRequest r, String type, String titleKey, String bodyKey, Map<String, Object> extra) {
        Map<String, Object> params = new LinkedHashMap<>(extra);
        params.put("requestId", r.getId());
        inbox.add(r.getRequester().getId(), type, titleKey, bodyKey, params, r.getId());
    }

    /** Tells donors who were alerted or had accepted that they no longer need to go. */
    private void notifyDonorsOfClosure(BloodRequest r) {
        List<RequestMatch> open = matchRepository.findByRequestIdAndStatusIn(r.getId(),
                List.of(MatchStatus.PENDING, MatchStatus.ACCEPTED));
        for (RequestMatch m : open) {
            if (m.getDonated() != null || m.getDonor().getUser() == null) continue;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("requestId", r.getId());
            params.put("hospital", r.getHospitalName());
            inbox.add(m.getDonor().getUser().getId(), "REQUEST_CLOSED", "notifications.closed.title",
                    "notifications.closed.body", params, r.getId());
        }
    }
}
