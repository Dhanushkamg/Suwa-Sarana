package com.suwasarana.api.request;

import com.suwasarana.api.request.dto.CreateRequestDto;
import com.suwasarana.api.request.dto.RequestResponseDto;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.matching.MatchingEngine;
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

    @Transactional
    public RequestResponseDto createRequest(Long userId, CreateRequestDto dto) {
        User requester = userRepository.findById(userId).orElseThrow();

        if (!requester.isVerified() && requester.getRole() != Role.HOSPITAL_REQUESTER && requester.getRole() != Role.ADMIN && dto.getUrgency() == Urgency.CRITICAL) {
            throw new RuntimeException("Unverified users cannot create CRITICAL requests.");
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

        // Trigger immediate matching
        matchingEngine.runMatchingForRequest(savedRequest.getId());

        return mapToDto(savedRequest);
    }

    public RequestResponseDto getRequestById(Long id) {
        BloodRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        return mapToDto(request);
    }

    public List<RequestResponseDto> getAllRequests() {
        return requestRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<RequestResponseDto> getUserRequests(Long userId) {
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    private RequestResponseDto mapToDto(BloodRequest request) {
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
        return dto;
    }
}
