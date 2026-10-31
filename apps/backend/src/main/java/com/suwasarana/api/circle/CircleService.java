package com.suwasarana.api.circle;

import com.suwasarana.api.common.Times;
import com.suwasarana.api.circle.dto.*;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.exception.CircleExpiredException;
import com.suwasarana.api.exception.CircleNotFoundException;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CircleService {

    private static final Logger log = LoggerFactory.getLogger(CircleService.class);

    /** Neutral texts: they never contain the token or any other detail of the request. */
    static final String MSG_NOT_FOUND = "This invitation link is not valid.";
    static final String MSG_INACTIVE = "This invitation link is no longer active.";

    private final RequestCircleRepository requestCircleRepository;
    private final CircleResponseRepository circleResponseRepository;
    private final RequestRepository requestRepository;
    private final CircleTokens tokens;
    private final CircleProperties properties;
    private final List<PledgeNotifier> notifiers;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Autowired
    public CircleService(RequestCircleRepository requestCircleRepository,
                         CircleResponseRepository circleResponseRepository,
                         RequestRepository requestRepository,
                         CircleTokens tokens,
                         CircleProperties properties,
                         List<PledgeNotifier> notifiers) {
        this.requestCircleRepository = requestCircleRepository;
        this.circleResponseRepository = circleResponseRepository;
        this.requestRepository = requestRepository;
        this.tokens = tokens;
        this.properties = properties;
        this.notifiers = notifiers == null ? List.of() : notifiers;
    }

    @Transactional
    public CircleInviteDto createOrGetCircle(Long requestId, Long userId) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Blood request not found"));

        if (!request.getRequester().getId().equals(userId)) {
            throw new AccessDeniedException("Only the request creator can generate or manage private circles");
        }
        if (!properties.isActive(request.getStatus())) {
            throw ApiException.conflict("This blood request is no longer active, so an invite link cannot be created.");
        }

        RequestCircle circle = requestCircleRepository.findByRequestId(requestId).orElse(null);
        String token = null;
        if (circle != null) {
            token = tokens.decrypt(circle.getInviteTokenEnc()).orElse(null);
            if (token == null || !tokens.hash(token).equals(circle.getInviteTokenHash())) {
                // The protected copy cannot be read (legacy row not yet converted, or the key was changed):
                // issue a fresh link. The previous link stops working, which is the safe failure.
                log.warn("Circle {} invite token is not recoverable; issuing a new one", circle.getId());
                token = tokens.generate();
                circle.setInviteTokenHash(tokens.hash(token));
                circle.setInviteTokenEnc(tokens.encrypt(token));
                circle = requestCircleRepository.save(circle);
            }
        } else {
            token = tokens.generate();
            LocalDateTime expiresAt = request.getExpiresAt() != null ? request.getExpiresAt() : Times.utcNow().plusDays(2);
            circle = requestCircleRepository.save(
                    new RequestCircle(request, tokens.hash(token), tokens.encrypt(token), expiresAt));
        }

        String inviteUrl = frontendBaseUrl + "/circle/" + token;
        return new CircleInviteDto(request.getId(), token, inviteUrl, circle.getCreatedAt(), circle.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public CircleInfoDto getCircleInfo(String token) {
        RequestCircle circle = requestCircleRepository.findByInviteTokenHash(tokens.hash(token))
                .orElseThrow(() -> new CircleNotFoundException(MSG_NOT_FOUND));
        assertUsable(circle);

        BloodRequest req = circle.getRequest();
        return new CircleInfoDto(
                token,
                req.getPatientBloodType(),
                req.getUnitsNeeded(),
                req.getUrgency(),
                req.getHospitalName(),
                req.getDistrict(),
                circle.getExpiresAt(),
                false
        );
    }

    @Transactional
    public CircleResponseDto submitResponse(String token, SubmitCircleResponseDto dto) {
        // Row lock: concurrent pledges to one circle are processed one at a time so the caps below are exact.
        RequestCircle circle = requestCircleRepository.findByInviteTokenHashForUpdate(tokens.hash(token))
                .orElseThrow(() -> new CircleNotFoundException(MSG_NOT_FOUND));
        assertUsable(circle);

        String phone = PhoneNumbers.normalise(dto.getResponderPhone());
        if (phone == null) {
            throw ApiException.badRequest("Please enter a valid phone number, for example 0771234567 or +94771234567.");
        }
        String name = clean(dto.getResponderName());
        if (name.length() < 2) {
            throw ApiException.badRequest("Please enter your name.");
        }
        String notes = dto.getNotes() == null ? null : clean(dto.getNotes());
        if (notes != null && notes.isEmpty()) {
            notes = null;
        }

        if (circleResponseRepository.countByCircleId(circle.getId()) >= properties.getMaxPledgesPerCircle()) {
            throw ApiException.conflict("This request already has enough volunteers. Thank you for offering to help.");
        }
        if (circleResponseRepository.countByCircleIdAndResponderPhone(circle.getId(), phone)
                >= properties.getMaxPledgesPerPhonePerCircle()) {
            throw ApiException.conflict("This phone number has already volunteered for this request.");
        }
        if (circleResponseRepository.countByResponderPhoneAndRespondedAtAfter(phone, Times.utcNow().minusDays(1))
                >= properties.getMaxPledgesPerPhonePerDay()) {
            throw ApiException.tooManyRequests("This phone number has made too many pledges today. Please try again tomorrow.");
        }

        CircleResponse saved = circleResponseRepository.save(
                new CircleResponse(circle, name, phone, dto.getBloodType().trim(), notes));

        boolean notified = notifyRequester(circle, saved);
        CircleResponseDto out = new CircleResponseDto(saved.getId(), saved.getResponderName(), saved.getResponderPhone(),
                saved.getBloodType(), saved.getNotes(), saved.getRespondedAt());
        out.setRequesterNotified(notified);
        return out;
    }

    @Transactional(readOnly = true)
    public List<CircleResponseDto> getCircleResponses(Long requestId, Long userId) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Blood request not found"));

        if (!request.getRequester().getId().equals(userId)) {
            throw new AccessDeniedException("Only the request creator can view private circle responses");
        }

        RequestCircle circle = requestCircleRepository.findByRequestId(requestId)
                .orElseThrow(() -> new CircleNotFoundException("No private circle found for this request"));

        return circleResponseRepository.findByCircleIdOrderByRespondedAtDesc(circle.getId())
                .stream()
                .map(r -> new CircleResponseDto(r.getId(), r.getResponderName(), r.getResponderPhone(),
                        r.getBloodType(), r.getNotes(), r.getRespondedAt()))
                .collect(Collectors.toList());
    }

    /** A token only works while its own expiry has not passed AND the request is still active. */
    private void assertUsable(RequestCircle circle) {
        if (circle.isExpired() || !properties.isActive(circle.getRequest().getStatus())) {
            throw new CircleExpiredException(MSG_INACTIVE);
        }
    }

    private boolean notifyRequester(RequestCircle circle, CircleResponse pledge) {
        BloodRequest request = circle.getRequest();
        int count = (int) circleResponseRepository.countByCircleId(circle.getId());
        boolean any = false;
        for (PledgeNotifier notifier : notifiers) {
            try {
                any |= notifier.pledgeReceived(request.getId(), request.getRequester().getId(),
                        pledge.getResponderName(), pledge.getBloodType(), count);
            } catch (RuntimeException e) {
                log.warn("Pledge notifier {} failed: {}", notifier.getClass().getSimpleName(), e.getClass().getSimpleName());
            }
        }
        return any;
    }

    /** Trims and removes control characters (a NUL byte makes PostgreSQL reject the row) and line breaks. */
    private static String clean(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) {
                if (c == '\n' || c == '\r' || c == '\t') {
                    sb.append(' ');
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
