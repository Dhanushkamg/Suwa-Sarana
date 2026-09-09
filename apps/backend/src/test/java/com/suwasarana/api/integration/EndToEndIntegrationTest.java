package com.suwasarana.api.integration;

import com.suwasarana.api.auth.AuthService;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.matching.MatchStatus;
import com.suwasarana.api.matching.MatchingEngine;
import com.suwasarana.api.matching.RequestMatch;
import com.suwasarana.api.matching.RequestMatchRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.junit.jupiter.api.Disabled("Requires running Postgres instance with earthdistance")
public class EndToEndIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private RequestMatchRepository requestMatchRepository;

    @Autowired
    private MatchingEngine matchingEngine;

    @Test
    @Transactional
    public void testFullMatchingFlow() {
        // 1. Setup Requester
        String requesterEmail = "requester_" + UUID.randomUUID() + "@suwasarana.com";
        RegisterDto requesterDto = new RegisterDto();
        requesterDto.setEmail(requesterEmail);
        requesterDto.setPhoneNumber("0710000001");
        requesterDto.setPassword("securePassword123");
        requesterDto.setRole(Role.REQUESTER);
        AuthResponse requesterAuth = authService.register(requesterDto);
        assertNotNull(requesterAuth.getAccessToken());

        User requester = userRepository.findById(requesterAuth.getUserId()).orElseThrow();

        // 2. Setup Eligible Donor (Colombo)
        String donorEmail = "donor_" + UUID.randomUUID() + "@suwasarana.com";
        RegisterDto donorDto = new RegisterDto();
        donorDto.setEmail(donorEmail);
        donorDto.setPhoneNumber("0710000002");
        donorDto.setPassword("securePassword123");
        donorDto.setRole(Role.DONOR);
        AuthResponse donorAuth = authService.register(donorDto);

        User donorUser = userRepository.findById(donorAuth.getUserId()).orElseThrow();
        DonorProfile profile = new DonorProfile();
        profile.setUser(donorUser);
        profile.setBloodType("O+");
        profile.setDateOfBirth(LocalDate.now().minusYears(25)); // 25 years old
        profile.setWeightKg(new BigDecimal("70.00")); // Eligible weight
        profile.setAvailable(true);
        profile.setReliabilityScore(new BigDecimal("50.00"));
        profile.setDistrict("Colombo");
        profile.setLatitude(6.9271); // Colombo coordinates
        profile.setLongitude(79.8612);
        donorRepository.save(profile);

        // 3. Setup Distant Donor (Kandy) - Should NOT match initially (Radius 5km)
        String distantDonorEmail = "dist_donor_" + UUID.randomUUID() + "@suwasarana.com";
        RegisterDto distantDonorDto = new RegisterDto();
        distantDonorDto.setEmail(distantDonorEmail);
        distantDonorDto.setPhoneNumber("0710000003");
        distantDonorDto.setPassword("securePassword123");
        distantDonorDto.setRole(Role.DONOR);
        AuthResponse distDonorAuth = authService.register(distantDonorDto);

        User distDonorUser = userRepository.findById(distDonorAuth.getUserId()).orElseThrow();
        DonorProfile distProfile = new DonorProfile();
        distProfile.setUser(distDonorUser);
        distProfile.setBloodType("O+");
        distProfile.setDateOfBirth(LocalDate.now().minusYears(30));
        distProfile.setWeightKg(new BigDecimal("80.00"));
        distProfile.setAvailable(true);
        distProfile.setReliabilityScore(new BigDecimal("50.00"));
        distProfile.setDistrict("Kandy");
        distProfile.setLatitude(7.2906); // Kandy coordinates (approx 100km away)
        distProfile.setLongitude(80.6337);
        donorRepository.save(distProfile);

        // 4. Create Blood Request in Colombo
        BloodRequest request = new BloodRequest();
        request.setRequester(requester);
        request.setPatientBloodType("O+");
        request.setUrgency(Urgency.URGENT);
        request.setHospitalName("National Hospital Colombo");
        request.setDistrict("Colombo");
        request.setLatitude(6.9271); 
        request.setLongitude(79.8612);
        request.setUnitsNeeded((short) 1);
        request.setExpiresAt(LocalDateTime.now().plusDays(2));
        request.setLastEscalatedAt(LocalDateTime.now());
        request.setCurrentRadiusKm((short) 5); // 5km radius initially
        BloodRequest savedRequest = requestRepository.save(request);

        // 5. Run Matching Engine
        matchingEngine.runMatchingForRequest(savedRequest.getId());

        // 6. Assertions
        List<RequestMatch> matches = requestMatchRepository.findByRequestId(savedRequest.getId());
        
        // Should only match the Colombo donor, not the Kandy donor
        assertEquals(1, matches.size());
        
        RequestMatch match = matches.get(0);
        assertEquals(donorUser.getId(), match.getDonor().getUser().getId());
        assertEquals(MatchStatus.PENDING, match.getStatus());
    }
}
