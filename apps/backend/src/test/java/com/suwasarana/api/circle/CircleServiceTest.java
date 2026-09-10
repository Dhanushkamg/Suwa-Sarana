package com.suwasarana.api.circle;

import com.suwasarana.api.circle.dto.*;
import com.suwasarana.api.exception.CircleExpiredException;
import com.suwasarana.api.exception.CircleNotFoundException;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircleServiceTest {

    @Mock
    private RequestCircleRepository requestCircleRepository;

    @Mock
    private CircleResponseRepository circleResponseRepository;

    @Mock
    private RequestRepository requestRepository;

    @InjectMocks
    private CircleService circleService;

    private User requester;
    private BloodRequest bloodRequest;
    private RequestCircle circle;

    @BeforeEach
    void setUp() {
        requester = new User();
        requester.setId(10L);
        requester.setEmail("requester@test.com");

        bloodRequest = new BloodRequest();
        bloodRequest.setId(100L);
        bloodRequest.setRequester(requester);
        bloodRequest.setPatientBloodType("B+");
        bloodRequest.setUnitsNeeded((short) 2);
        bloodRequest.setUrgency(Urgency.CRITICAL);
        bloodRequest.setHospitalName("Kandy General Hospital");
        bloodRequest.setDistrict("Kandy");
        bloodRequest.setExpiresAt(LocalDateTime.now().plusHours(24));

        circle = new RequestCircle(bloodRequest, "test-token-123456789012345678901234567890123456789012345678901234567890", LocalDateTime.now().plusHours(24));
        circle.setId(1L);
    }

    @Test
    @DisplayName("Should create a new circle and generate invite URL when none exists")
    void createOrGetCircle_createsNew() {
        when(requestRepository.findById(100L)).thenReturn(Optional.of(bloodRequest));
        when(requestCircleRepository.findByRequestId(100L)).thenReturn(Optional.empty());
        when(requestCircleRepository.save(any(RequestCircle.class))).thenAnswer(inv -> {
            RequestCircle c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CircleInviteDto result = circleService.createOrGetCircle(100L, 10L);

        assertNotNull(result);
        assertEquals(100L, result.getRequestId());
        assertNotNull(result.getInviteToken());
        assertEquals(64, result.getInviteToken().length());
        assertTrue(result.getInviteUrl().contains("/circle/"));
    }

    @Test
    @DisplayName("Should return existing circle invite if already created")
    void createOrGetCircle_returnsExisting() {
        when(requestRepository.findById(100L)).thenReturn(Optional.of(bloodRequest));
        when(requestCircleRepository.findByRequestId(100L)).thenReturn(Optional.of(circle));

        CircleInviteDto result = circleService.createOrGetCircle(100L, 10L);

        assertNotNull(result);
        assertEquals(circle.getInviteToken(), result.getInviteToken());
        verify(requestCircleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject circle generation from non-owning requester with AccessDeniedException")
    void createOrGetCircle_rejectsNonOwner() {
        when(requestRepository.findById(100L)).thenReturn(Optional.of(bloodRequest));

        assertThrows(AccessDeniedException.class, () -> {
            circleService.createOrGetCircle(100L, 999L); // Different user ID
        });
    }

    @Test
    @DisplayName("Should return safe public info for guests without sensitive patient identity")
    void getCircleInfo_returnsSafeInfo() {
        when(requestCircleRepository.findByInviteToken("valid-token")).thenReturn(Optional.of(circle));

        CircleInfoDto info = circleService.getCircleInfo("valid-token");

        assertNotNull(info);
        assertEquals("B+", info.getPatientBloodType());
        assertEquals("Kandy General Hospital", info.getHospitalName());
        assertEquals("Kandy", info.getDistrict());
        assertEquals(Urgency.CRITICAL, info.getUrgency());
        assertFalse(info.isExpired());
    }

    @Test
    @DisplayName("Should throw CircleNotFoundException when token does not exist")
    void getCircleInfo_throwsWhenNotFound() {
        when(requestCircleRepository.findByInviteToken("invalid-token")).thenReturn(Optional.empty());

        assertThrows(CircleNotFoundException.class, () -> {
            circleService.getCircleInfo("invalid-token");
        });
    }

    @Test
    @DisplayName("Should throw CircleExpiredException when token is expired")
    void getCircleInfo_throwsWhenExpired() {
        RequestCircle expiredCircle = new RequestCircle(bloodRequest, "expired-token", LocalDateTime.now().minusHours(1));
        when(requestCircleRepository.findByInviteToken("expired-token")).thenReturn(Optional.of(expiredCircle));

        assertThrows(CircleExpiredException.class, () -> {
            circleService.getCircleInfo("expired-token");
        });
    }

    @Test
    @DisplayName("Should successfully record volunteer response from guest")
    void submitResponse_recordsVolunteer() {
        when(requestCircleRepository.findByInviteToken("valid-token")).thenReturn(Optional.of(circle));
        when(circleResponseRepository.save(any(CircleResponse.class))).thenAnswer(inv -> {
            CircleResponse r = inv.getArgument(0);
            r.setId(10L);
            return r;
        });

        SubmitCircleResponseDto dto = new SubmitCircleResponseDto("Kasun Perera", "0771234567", "B+", "Available in 1 hour");
        CircleResponseDto response = circleService.submitResponse("valid-token", dto);

        assertNotNull(response);
        assertEquals("Kasun Perera", response.getResponderName());
        assertEquals("0771234567", response.getResponderPhone());
        assertEquals("B+", response.getBloodType());

        ArgumentCaptor<CircleResponse> captor = ArgumentCaptor.forClass(CircleResponse.class);
        verify(circleResponseRepository).save(captor.capture());
        assertEquals("Kasun Perera", captor.getValue().getResponderName());
    }

    @Test
    @DisplayName("Should return circle responses list only to owning requester")
    void getCircleResponses_returnsListForOwner() {
        when(requestRepository.findById(100L)).thenReturn(Optional.of(bloodRequest));
        when(requestCircleRepository.findByRequestId(100L)).thenReturn(Optional.of(circle));

        CircleResponse resp1 = new CircleResponse(circle, "Volunteer 1", "0771111111", "B+", "Can come immediately");
        resp1.setId(1L);

        when(circleResponseRepository.findByCircleIdOrderByRespondedAtDesc(circle.getId()))
                .thenReturn(List.of(resp1));

        List<CircleResponseDto> responses = circleService.getCircleResponses(100L, 10L);

        assertEquals(1, responses.size());
        assertEquals("Volunteer 1", responses.get(0).getResponderName());
    }
}
