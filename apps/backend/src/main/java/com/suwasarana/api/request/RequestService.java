package com.suwasarana.api.request;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.request.dto.CreateRequestDto;
import com.suwasarana.api.request.dto.RequestResponseDto;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import com.suwasarana.api.matching.MatchingEngine;
import com.suwasarana.api.ai.RequestTriageAiService;
import com.suwasarana.api.exception.RequesterNotVerifiedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RequestService {

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchingEngine matchingEngine;

    @Autowired
    private RequestTriageAiService requestTriageAiService;

    @Transactional
    public RequestResponseDto createRequest(Long userId, CreateRequestDto dto, boolean isAdmin) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (dto.getUrgency() == Urgency.CRITICAL && requester.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new RequesterNotVerifiedException("Unverified users cannot create CRITICAL requests.");
        }

        BloodRequest request = new BloodRequest();
        request.setRequester(requester);
        request.setPatientBloodType(dto.getPatientBloodType());
        request.setUnitsNeeded(dto.getUnitsNeeded());
        request.setUrgency(dto.getUrgency());
        request.setHospitalName(dto.getHospitalName());
        request.setDistrict(dto.getDistrict());
        request.setLatitude(dto.getLatitude());
        request.setLongitude(dto.getLongitude());
        
        request.setStatus(RequestStatus.ESCALATING);
        request.setCurrentRadiusKm((short) 5);
        request.setExpiresAt(LocalDateTime.now().plusHours(6));

        BloodRequest savedRequest = requestRepository.save(request);

        // Perform AI Fraud & Duplicate Triage
        try {
            savedRequest = requestTriageAiService.triageRequest(savedRequest);
        } catch (Exception e) {
            // Triage error should never block request creation
        }

        // Trigger immediate matching
        matchingEngine.runMatchingForRequest(savedRequest.getId());

        return mapToDto(savedRequest, isAdmin);
    }

    public RequestResponseDto getRequestById(Long id, boolean isAdmin) {
        BloodRequest request = requestRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Request not found"));
        return mapToDto(request, isAdmin);
    }

    @Transactional
    public void cancelRequest(Long userId, Long requestId) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Request not found"));
        
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        
        // Only the owner or an Admin can cancel
        if (!request.getRequester().getId().equals(userId) && requester.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Cannot cancel another user's request");
        }
        
        request.setStatus(RequestStatus.CANCELLED);
        requestRepository.save(request);
    }

    public List<RequestResponseDto> getAllRequests(boolean isAdmin) {
        // Admins see everything; everyone else only sees requests that are still active
        List<BloodRequest> requests = isAdmin
                ? requestRepository.findAllByOrderByCreatedAtDesc()
                : requestRepository.findByStatusInOrderByCreatedAtDesc(
                        List.of(RequestStatus.OPEN, RequestStatus.ESCALATING));
        return requests.stream()
                .map(r -> mapToDto(r, isAdmin))
                .toList();
    }

    public List<RequestResponseDto> getUserRequests(Long userId, boolean isAdmin) {
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(r -> mapToDto(r, isAdmin))
                .toList();
    }

    // Fraud-triage fields are only exposed to admins
    private RequestResponseDto mapToDto(BloodRequest request, boolean isAdmin) {
        RequestResponseDto dto = new RequestResponseDto();
        dto.setId(request.getId());
        dto.setPatientBloodType(request.getPatientBloodType());
        dto.setUnitsNeeded(request.getUnitsNeeded());
        dto.setUrgency(request.getUrgency());
        dto.setHospitalName(request.getHospitalName());
        dto.setDistrict(request.getDistrict());
        dto.setLatitude(request.getLatitude());
        dto.setLongitude(request.getLongitude());
        dto.setStatus(request.getStatus());
        dto.setCurrentRadiusKm(request.getCurrentRadiusKm());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setExpiresAt(request.getExpiresAt());
        if (isAdmin) {
            dto.setFraudRiskScore(request.getFraudRiskScore());
            dto.setAiFlagReason(request.getAiFlagReason());
            dto.setTriagedAt(request.getTriagedAt());
        }
        return dto;
    }
}
