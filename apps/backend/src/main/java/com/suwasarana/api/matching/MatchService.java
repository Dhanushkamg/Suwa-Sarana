package com.suwasarana.api.matching;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.donor.DonationRecorder;
import com.suwasarana.api.donor.DonationRecordRepository;
import com.suwasarana.api.donor.ReliabilityScoreService;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestLifecycleService;
import com.suwasarana.api.request.dto.RequestMatchDto;
import com.suwasarana.api.request.dto.RequestMatchesDto;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.shared.CodedApiException;
import com.suwasarana.api.shared.ErrorCodes;
import com.suwasarana.api.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MatchService {

    @Autowired
    private RequestMatchRepository requestMatchRepository;

    @Autowired
    private ReliabilityScoreService reliabilityScoreService;

    @Autowired
    private DonationRecorder donationRecorder;

    @Autowired
    private DonationRecordRepository donationRecordRepository;

    @Autowired
    private RequestLifecycleService lifecycle;

    @Autowired
    private com.suwasarana.api.request.RequestRepository requestRepository;

    @Autowired
    private AppClock clock;

    @Transactional
    public void respondToMatch(Long matchId, Long donorUserId, MatchStatus response) {
        if (response != MatchStatus.ACCEPTED && response != MatchStatus.DECLINED) {
            throw ApiException.badRequest("Response must be ACCEPTED or DECLINED");
        }

        RequestMatch match = requestMatchRepository.findById(matchId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.MATCH_NOT_FOUND, "Match not found"));

        if (!match.getDonor().getUser().getId().equals(donorUserId)) {
            throw ApiException.forbidden("Unauthorized to respond to this match");
        }

        BloodRequest request = match.getRequest();
        if (match.getStatus() != MatchStatus.PENDING) {
            throw CodedApiException.conflict(ErrorCodes.MATCH_ALREADY_ANSWERED, "This match has already been responded to");
        }
        if (request.getStatus() != com.suwasarana.api.request.RequestStatus.OPEN
                && request.getStatus() != com.suwasarana.api.request.RequestStatus.ESCALATING) {
            throw CodedApiException.conflict(ErrorCodes.REQUEST_NOT_ACTIVE, "This request is no longer active");
        }

        match.setStatus(response);
        match.setRespondedAt(clock.utcNow());
        requestMatchRepository.save(match);

        if (response == MatchStatus.ACCEPTED) {
            // Requester is told, and enough acceptances flip the request to MATCHED
            lifecycle.onDonorAccepted(request);
        }
    }

    /**
     * The requester (or admin) records whether an accepted donor actually gave blood.
     * donated=true goes through {@link DonationRecorder} (history, totals, deferral, reliability), and may complete the
     * request. donated=false is a no-show: reliability drops and a MATCHED request without enough live donors re-opens.
     */
    @Transactional
    public void markMatchDonated(Long matchId, Long requesterId, boolean isAdmin, boolean donated) {
        RequestMatch match = requestMatchRepository.findById(matchId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.MATCH_NOT_FOUND, "Match not found"));

        if (!isAdmin && !match.getRequest().getRequester().getId().equals(requesterId)) {
            throw ApiException.forbidden("Unauthorized to confirm donation for this request");
        }
        if (match.getStatus() != MatchStatus.ACCEPTED) {
            throw CodedApiException.conflict(ErrorCodes.MATCH_NOT_ACCEPTED, "Only accepted matches can be marked as donated");
        }
        if (match.getDonated() != null) {
            throw CodedApiException.conflict(ErrorCodes.DONATION_ALREADY_RECORDED, "Donation outcome has already been recorded");
        }

        match.setDonated(donated);
        requestMatchRepository.save(match);

        BloodRequest request = match.getRequest();
        if (donated) {
            donationRecorder.recordForMatch(match);
            lifecycle.onDonationRecorded(request);
        } else {
            reliabilityScoreService.recordNoShow(match.getDonor().getId());
            lifecycle.onDonorNoShow(request);
        }
    }

    /**
     * The requester view of a request's matches. PRIVACY: the phone number of a donor is included only when the
     * match is ACCEPTED and the outcome is not yet recorded; everything else shows the first name, blood type,
     * distance and status only.
     */
    @Transactional(readOnly = true)
    public RequestMatchesDto listMatchesForRequest(Long requestId, Long actorId, boolean admin) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.REQUEST_NOT_FOUND, "Request not found"));
        if (!admin && !request.getRequester().getId().equals(actorId)) {
            throw CodedApiException.notFound(ErrorCodes.REQUEST_NOT_FOUND, "Request not found");
        }
        List<RequestMatch> matches = requestMatchRepository.findByRequestIdOrderByDistanceKmAscIdAsc(requestId);

        RequestMatchesDto out = new RequestMatchesDto();
        out.setRequestId(requestId);
        out.setUnitsNeeded(request.getUnitsNeeded());
        out.setAcceptedCount(requestMatchRepository.countLiveAccepted(requestId));
        out.setConfirmedDonations(donationRecordRepository.countByRequestId(requestId));
        out.setMatches(matches.stream().map(this::toRequesterDto).toList());
        return out;
    }

    private RequestMatchDto toRequesterDto(RequestMatch m) {
        User donorUser = m.getDonor().getUser();
        RequestMatchDto dto = new RequestMatchDto();
        dto.setMatchId(m.getId());
        dto.setDonorFirstName(donorUser == null ? null : donorUser.getFirstName());
        dto.setBloodType(m.getDonor().getBloodType());
        dto.setDistanceKm(m.getDistanceKm());
        dto.setStatus(m.getStatus());
        dto.setNotifiedAt(m.getCreatedAt());
        dto.setRespondedAt(m.getRespondedAt());
        dto.setDonated(m.getDonated());
        if (m.getStatus() == MatchStatus.ACCEPTED && m.getDonated() == null && donorUser != null) {
            dto.setContact(new RequestMatchDto.Contact(donorUser.getPhoneNumber()));
        }
        return dto;
    }
}
