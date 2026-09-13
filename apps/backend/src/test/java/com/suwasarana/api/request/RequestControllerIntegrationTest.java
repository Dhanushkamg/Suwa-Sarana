package com.suwasarana.api.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.auth.AuthService;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.request.dto.CreateRequestDto;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class RequestControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private com.suwasarana.api.auth.RefreshTokenRepository refreshTokenRepository;

    private String unverifiedToken;
    private String verifiedToken;
    private String otherVerifiedToken;
    
    private Long verifiedUserId;
    private Long activeRequestId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        requestRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Unverified Requester
        RegisterDto unverifiedDto = new RegisterDto();
        unverifiedDto.setEmail("unverified_" + UUID.randomUUID() + "@test.com");
        unverifiedDto.setPhoneNumber("0771000001");
        unverifiedDto.setPassword("password12");
        unverifiedDto.setRole(Role.REQUESTER);
        AuthResponse unverifiedAuth = authService.register(unverifiedDto, "127.0.0.1");
        unverifiedToken = unverifiedAuth.getAccessToken();

        // 2. Verified Requester
        RegisterDto verifiedDto = new RegisterDto();
        verifiedDto.setEmail("verified_" + UUID.randomUUID() + "@test.com");
        verifiedDto.setPhoneNumber("0771000002");
        verifiedDto.setPassword("password12");
        verifiedDto.setRole(Role.REQUESTER);
        AuthResponse verifiedAuth = authService.register(verifiedDto, "127.0.0.1");
        verifiedToken = verifiedAuth.getAccessToken();
        verifiedUserId = verifiedAuth.getUserId();
        
        // Mark as verified manually
        User vUser = userRepository.findById(verifiedUserId).orElseThrow();
        vUser.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(vUser);

        // 3. Other Verified Requester
        RegisterDto otherDto = new RegisterDto();
        otherDto.setEmail("other_" + UUID.randomUUID() + "@test.com");
        otherDto.setPhoneNumber("0771000003");
        otherDto.setPassword("password12");
        otherDto.setRole(Role.REQUESTER);
        AuthResponse otherAuth = authService.register(otherDto, "127.0.0.1");
        otherVerifiedToken = otherAuth.getAccessToken();
        
        User oUser = userRepository.findById(otherAuth.getUserId()).orElseThrow();
        oUser.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(oUser);

        // 4. Create Active Request for Verified User
        BloodRequest activeRequest = new BloodRequest();
        activeRequest.setRequester(vUser);
        activeRequest.setPatientBloodType("O+");
        activeRequest.setUnitsNeeded((short) 1);
        activeRequest.setUrgency(Urgency.URGENT);
        activeRequest.setHospitalName("Test Hospital");
        activeRequest.setDistrict("Colombo");
        activeRequest.setLatitude(6.9271);
        activeRequest.setLongitude(79.8612);
        activeRequest.setStatus(RequestStatus.OPEN);
        activeRequest.setCurrentRadiusKm((short) 5);
        activeRequest.setExpiresAt(java.time.LocalDateTime.now().plusDays(1));
        requestRepository.save(activeRequest);
        activeRequestId = activeRequest.getId();
    }

    @Test
    void unverifiedRequester_cannotCreateCriticalRequest() throws Exception {
        CreateRequestDto dto = new CreateRequestDto();
        dto.setPatientBloodType("A+");
        dto.setUnitsNeeded((short) 2);
        dto.setUrgency(Urgency.CRITICAL);
        dto.setHospitalName("Test Hospital");
        dto.setDistrict("Colombo");
        dto.setLatitude(6.9);
        dto.setLongitude(79.8);

        mockMvc.perform(post("/api/requests")
                .header("Authorization", "Bearer " + unverifiedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                // We updated the code to throw RequesterNotVerifiedException which is mapped to 403 Forbidden.
                .andExpect(status().isForbidden());
    }
    
    @Test
    void nonOwningUser_cannotCancelAnotherUsersRequest() throws Exception {
        mockMvc.perform(put("/api/requests/" + activeRequestId + "/cancel")
                .header("Authorization", "Bearer " + otherVerifiedToken))
                .andExpect(status().isForbidden());
    }
}
