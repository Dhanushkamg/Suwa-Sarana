package com.suwasarana.api.circle;

import com.suwasarana.api.circle.dto.*;
import com.suwasarana.api.exception.CircleExpiredException;
import com.suwasarana.api.exception.CircleNotFoundException;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CircleService {

    private final RequestCircleRepository requestCircleRepository;
    private final CircleResponseRepository circleResponseRepository;
    private final RequestRepository requestRepository;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Autowired
    public CircleService(RequestCircleRepository requestCircleRepository,
                         CircleResponseRepository circleResponseRepository,
                         RequestRepository requestRepository) {
        this.requestCircleRepository = requestCircleRepository;
        this.circleResponseRepository = circleResponseRepository;
        this.requestRepository = requestRepository;
    }

    @Transactional
    public CircleInviteDto createOrGetCircle(Long requestId, Long userId) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Blood request not found"));

        if (!request.getRequester().getId().equals(userId)) {
            throw new AccessDeniedException("Only the request creator can generate or manage private circles");
        }

        RequestCircle circle = requestCircleRepository.findByRequestId(requestId)
                .orElseGet(() -> {
                    String token = generateSecureToken();
                    LocalDateTime expiresAt = request.getExpiresAt() != null ? request.getExpiresAt() : LocalDateTime.now().plusDays(2);
                    RequestCircle newCircle = new RequestCircle(request, token, expiresAt);
                    return requestCircleRepository.save(newCircle);
                });

        String inviteUrl = frontendBaseUrl + "/circle/" + circle.getInviteToken();
        return new CircleInviteDto(request.getId(), circle.getInviteToken(), inviteUrl, circle.getCreatedAt(), circle.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public CircleInfoDto getCircleInfo(String token) {
        RequestCircle circle = requestCircleRepository.findByInviteToken(token)
                .orElseThrow(() -> new CircleNotFoundException("Replacement circle invite not found for token: " + token));

        if (circle.isExpired()) {
            throw new CircleExpiredException("This replacement circle invite link has expired.");
        }

        BloodRequest req = circle.getRequest();
        return new CircleInfoDto(
                circle.getInviteToken(),
                req.getPatientBloodType(),
                req.getUnitsNeeded(),
                req.getUrgency(),
                req.getHospitalName(),
                req.getDistrict(),
                circle.getExpiresAt(),
                circle.isExpired()
        );
    }

    @Transactional
    public CircleResponseDto submitResponse(String token, SubmitCircleResponseDto dto) {
        RequestCircle circle = requestCircleRepository.findByInviteToken(token)
                .orElseThrow(() -> new CircleNotFoundException("Replacement circle invite not found for token: " + token));

        if (circle.isExpired()) {
            throw new CircleExpiredException("Cannot submit response: This replacement circle invite link has expired.");
        }

        CircleResponse response = new CircleResponse(
                circle,
                dto.getResponderName().trim(),
                dto.getResponderPhone().trim(),
                dto.getBloodType().trim(),
                dto.getNotes() != null ? dto.getNotes().trim() : null
        );

        CircleResponse saved = circleResponseRepository.save(response);
        return new CircleResponseDto(saved.getId(), saved.getResponderName(), saved.getResponderPhone(),
                saved.getBloodType(), saved.getNotes(), saved.getRespondedAt());
    }

    @Transactional(readOnly = true)
    public List<CircleResponseDto> getCircleResponses(Long requestId, Long userId) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Blood request not found"));

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

    private String generateSecureToken() {
        return (UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "")).substring(0, 64);
    }
}
