package com.suwasarana.api.request;

import com.suwasarana.api.ai.RequestTriageAiService;
import com.suwasarana.api.donor.DonationRecordRepository;
import com.suwasarana.api.geo.District;
import com.suwasarana.api.geo.GeoBounds;
import com.suwasarana.api.matching.MatchingEngine;
import com.suwasarana.api.matching.RequestMatchRepository;
import com.suwasarana.api.request.dto.CreateRequestDto;
import com.suwasarana.api.request.dto.RequestResponseDto;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.shared.CodedApiException;
import com.suwasarana.api.shared.ErrorCodes;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class RequestService {

    private static final Set<RequestStatus> ACTIVE = EnumSet.of(RequestStatus.OPEN, RequestStatus.ESCALATING, RequestStatus.MATCHED);
    private static final Set<RequestStatus> VISIBLE_TO_DONORS = EnumSet.of(RequestStatus.OPEN, RequestStatus.ESCALATING);

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchingEngine matchingEngine;

    @Autowired
    private RequestTriageAiService requestTriageAiService;

    @Autowired
    private RequestLifecycleService lifecycle;

    @Autowired
    private RequesterEligibility requesterEligibility;

    @Autowired
    private RequestMatchRepository matchRepository;

    @Autowired
    private DonationRecordRepository donationRecordRepository;

    @Autowired
    private AppClock clock;

    /** How long a new request stays open. */
    @Value("${app.requests.ttl-hours:6}")
    private long ttlHours = 6;

    /** Most simultaneously active (OPEN / ESCALATING / MATCHED) requests one account may hold; admins are exempt. */
    @Value("${app.requests.max-active-per-user:3}")
    private int maxActivePerUser = 3;

    @Transactional
    public RequestResponseDto createRequest(Long userId, CreateRequestDto dto, boolean isAdmin) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.REQUESTER_NOT_ALLOWED, "User not found"));

        requesterEligibility.assertMayCreateRequest(requester, dto.getUrgency());

        String district = District.normalise(dto.getDistrict())
                .orElseThrow(() -> CodedApiException.badRequest(ErrorCodes.UNKNOWN_DISTRICT,
                        "Unknown district. Use one of the 25 official district names (GET /api/meta/districts)."));
        if (!GeoBounds.contains(dto.getLatitude(), dto.getLongitude())) {
            throw CodedApiException.badRequest(ErrorCodes.OUTSIDE_SRI_LANKA, "The hospital location must be inside Sri Lanka");
        }
        String hospital = dto.getHospitalName().trim();

        // Abuse limits: duplicate suppression first (more specific), then the per-account cap
        List<BloodRequest> duplicates = requestRepository.findDuplicates(userId, dto.getPatientBloodType(), hospital, ACTIVE);
        if (!duplicates.isEmpty()) {
            throw CodedApiException.conflict(ErrorCodes.REQUEST_DUPLICATE,
                    "You already have an active request for " + dto.getPatientBloodType() + " blood at this hospital (request #"
                            + duplicates.get(0).getId() + ").");
        }
        if (!isAdmin && requestRepository.countByRequesterIdAndStatusIn(userId, ACTIVE) >= maxActivePerUser) {
            throw CodedApiException.tooManyRequests(ErrorCodes.REQUEST_LIMIT_REACHED,
                    "You can have at most " + maxActivePerUser + " active requests. Close or fulfil one first.");
        }

        LocalDateTime now = clock.utcNow();
        BloodRequest request = new BloodRequest();
        request.setRequester(requester);
        request.setPatientBloodType(dto.getPatientBloodType());
        request.setUnitsNeeded(dto.getUnitsNeeded());
        request.setUrgency(dto.getUrgency());
        request.setHospitalName(hospital);
        request.setDistrict(district);
        request.setLatitude(dto.getLatitude());
        request.setLongitude(dto.getLongitude());

        request.setStatus(RequestStatus.ESCALATING);
        request.setCurrentRadiusKm((short) 5);
        request.setCreatedAt(now);
        request.setLastEscalatedAt(now);
        request.setExpiresAt(now.plusHours(ttlHours));

        BloodRequest savedRequest = requestRepository.save(request);

        // Perform AI Fraud & Duplicate Triage
        try {
            savedRequest = requestTriageAiService.triageRequest(savedRequest);
        } catch (Exception e) {
            // Triage error should never block request creation
        }

        // Immediate matching. Donors are alerted only after this transaction commits (see MatchNotificationListener).
        matchingEngine.runMatchingForRequest(savedRequest.getId());

        return mapToDto(savedRequest, View.OWNER, isAdmin);
    }

    /**
     * Who may read one request: its owner, an admin, or a donor who has been matched to it. Everyone else gets
     * the same 404 as for a request that does not exist, so ids cannot be enumerated.
     */
    @Transactional(readOnly = true)
    public RequestResponseDto getRequestById(Long id, Long viewerId, boolean isAdmin) {
        BloodRequest request = requestRepository.findById(id)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.REQUEST_NOT_FOUND, "Request not found"));
        boolean owner = request.getRequester() != null && request.getRequester().getId().equals(viewerId);
        if (isAdmin || owner) {
            return mapToDto(request, View.OWNER, isAdmin);
        }
        if (matchRepository.existsByRequestIdAndDonorUserId(id, viewerId)) {
            return mapToDto(request, View.MATCHED_DONOR, false);
        }
        throw CodedApiException.notFound(ErrorCodes.REQUEST_NOT_FOUND, "Request not found");
    }

    /** Legacy cancel endpoint: same as closing with the default reason. */
    @Transactional
    public void cancelRequest(Long userId, Long requestId) {
        User caller = userRepository.findById(userId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.REQUESTER_NOT_ALLOWED, "User not found"));
        lifecycle.close(requestId, userId, caller.getRole() == com.suwasarana.api.user.Role.ADMIN, CloseReason.NO_LONGER_NEEDED);
    }

    @Transactional
    public RequestResponseDto fulfilRequest(Long userId, Long requestId, boolean isAdmin) {
        return mapToDto(lifecycle.fulfil(requestId, userId, isAdmin), View.OWNER, isAdmin);
    }

    @Transactional
    public RequestResponseDto closeRequest(Long userId, Long requestId, boolean isAdmin, CloseReason reason) {
        return mapToDto(lifecycle.close(requestId, userId, isAdmin, reason), View.OWNER, isAdmin);
    }

    /**
     * Browse list. Admins see everything. Everyone else sees only requests that still need donors, with the
     * location rounded to about 1 km: the exact hospital position is for the owner, admins and matched donors.
     */
    @Transactional(readOnly = true)
    public List<RequestResponseDto> getAllRequests(boolean isAdmin) {
        if (isAdmin) {
            return requestRepository.findAllByOrderByCreatedAtDesc().stream()
                    .map(r -> mapToDto(r, View.OWNER, true))
                    .toList();
        }
        return requestRepository.findByStatusInAndExpiresAtAfterOrderByCreatedAtDesc(VISIBLE_TO_DONORS, clock.utcNow()).stream()
                .map(r -> mapToDto(r, View.PUBLIC_LIST, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequestResponseDto> getUserRequests(Long userId, boolean isAdmin) {
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(r -> mapToDto(r, View.OWNER, isAdmin))
                .toList();
    }

    enum View { OWNER, MATCHED_DONOR, PUBLIC_LIST }

    // Fraud-triage fields are only exposed to admins; counts only to owner/admin; coordinates are coarse in the public list
    private RequestResponseDto mapToDto(BloodRequest request, View view, boolean isAdmin) {
        RequestResponseDto dto = new RequestResponseDto();
        dto.setId(request.getId());
        dto.setPatientBloodType(request.getPatientBloodType());
        dto.setUnitsNeeded(request.getUnitsNeeded());
        dto.setUrgency(request.getUrgency());
        dto.setHospitalName(request.getHospitalName());
        dto.setDistrict(request.getDistrict());
        if (view == View.PUBLIC_LIST) {
            dto.setLatitude(GeoBounds.round(request.getLatitude(), 2));
            dto.setLongitude(GeoBounds.round(request.getLongitude(), 2));
            dto.setCoarseLocation(true);
        } else {
            dto.setLatitude(request.getLatitude());
            dto.setLongitude(request.getLongitude());
        }
        dto.setStatus(request.getStatus());
        dto.setCurrentRadiusKm(request.getCurrentRadiusKm());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setExpiresAt(request.getExpiresAt());
        dto.setClosedAt(request.getClosedAt());
        if (view == View.OWNER) {
            dto.setCloseReason(request.getCloseReason());
            if (request.getId() != null) {
                dto.setAcceptedCount(matchRepository.countLiveAccepted(request.getId()));
                dto.setConfirmedDonations(donationRecordRepository.countByRequestId(request.getId()));
            }
        }
        if (isAdmin) {
            dto.setFraudRiskScore(request.getFraudRiskScore());
            dto.setAiFlagReason(request.getAiFlagReason());
            dto.setTriagedAt(request.getTriagedAt());
        }
        return dto;
    }
}
