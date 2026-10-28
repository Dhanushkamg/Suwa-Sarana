package com.suwasarana.api.donor;

import com.suwasarana.api.matching.RequestMatch;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.slot.DonationSlot;
import com.suwasarana.api.slot.SlotBooking;
import com.suwasarana.api.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * The ONLY place a donation is recorded. Both "requester confirms an accepted match as donated"
 * ({@code POST /api/matches/{id}/donated}) and "staff check a booked donor in at a slot" go through here, so the
 * four side effects can never drift apart:
 * <ol>
 *   <li>a {@link DonationRecord} (the donor's history, also used for CSV export);</li>
 *   <li>the donor's {@code totalDonations} and {@code livesHelpedEstimate};</li>
 *   <li>a {@code RECENT_DONATION} deferral that lasts until the end of the 4-month interval (so matching and slot
 *       booking stop offering the donor to anyone);</li>
 *   <li>the reliability reward.</li>
 * </ol>
 * Recording is idempotent per match / per booking: a second call returns the existing record unchanged.
 */
@Service
public class DonationRecorder {

    private static final Logger log = LoggerFactory.getLogger(DonationRecorder.class);
    private static final int LIVES_HELPED_PER_DONATION = 3;

    private final DonationRecordRepository recordRepository;
    private final DonorRepository donorRepository;
    private final DeferralRepository deferralRepository;
    private final ReliabilityScoreService reliabilityScoreService;
    private final AppClock clock;

    @Autowired
    public DonationRecorder(DonationRecordRepository recordRepository, DonorRepository donorRepository,
                            DeferralRepository deferralRepository, ReliabilityScoreService reliabilityScoreService,
                            AppClock clock) {
        this.recordRepository = recordRepository;
        this.donorRepository = donorRepository;
        this.deferralRepository = deferralRepository;
        this.reliabilityScoreService = reliabilityScoreService;
        this.clock = clock;
    }

    /** A requester/hospital confirmed that the donor of this ACCEPTED match gave blood. */
    @Transactional
    public DonationRecord recordForMatch(RequestMatch match) {
        return recordRepository.findByMatchId(match.getId()).orElseGet(() -> {
            BloodRequest request = match.getRequest();
            DonationRecord r = base(match.getDonor(), DonationSource.MATCH);
            r.setMatchId(match.getId());
            r.setRequestId(request.getId());
            User host = request.getRequester();
            r.setHostUserId(host != null ? host.getId() : null);
            r.setHostName(request.getHospitalName());
            r.setDistrict(request.getDistrict());
            return persist(match.getDonor(), r);
        });
    }

    /** Camp or hospital staff checked the booking in; that is the moment the donor gives blood. */
    @Transactional
    public DonationRecord recordForBooking(SlotBooking booking) {
        return recordRepository.findByBookingId(booking.getId()).orElseGet(() -> {
            DonationSlot slot = booking.getSlot();
            DonationRecord r = base(booking.getDonor(), DonationSource.SLOT_CHECKIN);
            r.setBookingId(booking.getId());
            if (slot.getCamp() != null) {
                r.setHostUserId(slot.getCamp().getOrganizer().getId());
                r.setDistrict(slot.getCamp().getDistrict());
            } else if (slot.getHospitalUser() != null) {
                r.setHostUserId(slot.getHospitalUser().getId());
            }
            r.setHostName(slot.resolveHostName());
            return persist(booking.getDonor(), r);
        });
    }

    private DonationRecord base(DonorProfile donor, DonationSource source) {
        DonationRecord r = new DonationRecord();
        r.setDonorId(donor.getId());
        r.setSource(source);
        r.setDonatedOn(clock.today());
        r.setBloodType(donor.getBloodType());
        r.setCreatedAt(clock.instant());
        return r;
    }

    private DonationRecord persist(DonorProfile donor, DonationRecord record) {
        DonationRecord saved = recordRepository.save(record);

        donor.setTotalDonations((donor.getTotalDonations() == null ? 0 : donor.getTotalDonations()) + 1);
        donor.setLivesHelpedEstimate((donor.getLivesHelpedEstimate() == null ? 0 : donor.getLivesHelpedEstimate())
                + LIVES_HELPED_PER_DONATION);
        donorRepository.save(donor);

        LocalDate on = saved.getDonatedOn();
        DonorDeferral deferral = new DonorDeferral();
        deferral.setDonor(donor);
        deferral.setReason(DeferralService.RECENT_DONATION);
        deferral.setDeferredFrom(on);
        deferral.setDeferredUntil(DeferralRulesConfig.deferralEndAfterDonation(on));
        deferralRepository.save(deferral);

        reliabilityScoreService.recordSuccessfulDonation(donor.getId());
        log.info("Recorded {} donation {} for donor {}", saved.getSource(), saved.getId(), donor.getId());
        return saved;
    }
}
