package com.suwasarana.api.circle;

import com.suwasarana.api.circle.dto.*;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.exception.CircleExpiredException;
import com.suwasarana.api.exception.CircleNotFoundException;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircleServiceTest {

    @Mock private RequestCircleRepository requestCircleRepository;
    @Mock private CircleResponseRepository circleResponseRepository;
    @Mock private RequestRepository requestRepository;

    private final CircleProperties properties = new CircleProperties();
    private final CircleTokens tokens = new CircleTokens("unit-test-key", "unit-test-jwt-secret");
    private final List<String> notified = new ArrayList<>();

    private CircleService circleService;
    private User requester;
    private BloodRequest bloodRequest;
    private RequestCircle circle;
    private String token;

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

        token = tokens.generate();
        circle = new RequestCircle(bloodRequest, tokens.hash(token), tokens.encrypt(token), LocalDateTime.now().plusHours(24));
        circle.setId(1L);

        PledgeNotifier recording = (requestId, userId, name, blood, count) -> notified.add(requestId + ":" + userId + ":" + count);
        circleService = new CircleService(requestCircleRepository, circleResponseRepository, requestRepository,
                tokens, properties, List.of(recording));
    }

    // ---------------------------------------------------------------- create / get

    @Test
    @DisplayName("A new circle stores only the hash and the encrypted copy, never the plain token")
    void createOrGetCircle_createsNew_storesNoPlaintext() {
        when(requestRepository.findById(100L)).thenReturn(Optional.of(bloodRequest));
        when(requestCircleRepository.findByRequestId(100L)).thenReturn(Optional.empty());
        when(requestCircleRepository.save(any(RequestCircle.class))).thenAnswer(inv -> {
            RequestCircle c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CircleInviteDto result = circleService.createOrGetCircle(100L, 10L);

        assertEquals(100L, result.getRequestId());
        assertEquals(64, result.getInviteToken().length());
        assertTrue(result.getInviteUrl().endsWith("/circle/" + result.getInviteToken()));

        ArgumentCaptor<RequestCircle> saved = ArgumentCaptor.forClass(RequestCircle.class);
        verify(requestCircleRepository).save(saved.capture());
        RequestCircle stored = saved.getValue();
        assertEquals(tokens.hash(result.getInviteToken()), stored.getInviteTokenHash());
        assertNull(stored.getInviteToken(), "the plain token must not be kept on the entity");
        assertFalse(stored.getInviteTokenHash().contains(result.getInviteToken()));
        assertFalse(stored.getInviteTokenEnc().contains(result.getInviteToken()));
        assertEquals(result.getInviteToken(), tokens.decrypt(stored.getInviteTokenEnc()).orElseThrow());
    }

    @Test
    @DisplayName("Asking again returns the very same link (decrypted from the stored copy) without saving")
    void createOrGetCircle_returnsSameLink() {
        when(requestRepository.findById(100L)).thenReturn(Optional.of(bloodRequest));
        when(requestCircleRepository.findByRequestId(100L)).thenReturn(Optional.of(circle));

        CircleInviteDto first = circleService.createOrGetCircle(100L, 10L);
        CircleInviteDto second = circleService.createOrGetCircle(100L, 10L);

        assertEquals(token, first.getInviteToken());
        assertEquals(token, second.getInviteToken());
        verify(requestCircleRepository, never()).save(any());
    }

    @Test
    @DisplayName("If the stored copy cannot be read (key changed / legacy row) a fresh link is issued and the old hash is replaced")
    void createOrGetCircle_rotatesWhenUnrecoverable() {
        circle.setInviteTokenEnc(null);
        when(requestRepository.findById(100L)).thenReturn(Optional.of(bloodRequest));
        when(requestCircleRepository.findByRequestId(100L)).thenReturn(Optional.of(circle));
        when(requestCircleRepository.save(any(RequestCircle.class))).thenAnswer(inv -> inv.getArgument(0));

        CircleInviteDto result = circleService.createOrGetCircle(100L, 10L);

        assertNotEquals(token, result.getInviteToken());
        assertEquals(tokens.hash(result.getInviteToken()), circle.getInviteTokenHash());
    }

    @Test
    @DisplayName("Only the owner may create the circle")
    void createOrGetCircle_rejectsNonOwner() {
        when(requestRepository.findById(100L)).thenReturn(Optional.of(bloodRequest));
        assertThrows(AccessDeniedException.class, () -> circleService.createOrGetCircle(100L, 999L));
    }

    @Test
    @DisplayName("No invite can be created for a cancelled, fulfilled or expired request")
    void createOrGetCircle_rejectsClosedRequest() {
        for (RequestStatus closed : new RequestStatus[] {RequestStatus.CANCELLED, RequestStatus.FULFILLED, RequestStatus.EXPIRED}) {
            bloodRequest.setStatus(closed);
            when(requestRepository.findById(100L)).thenReturn(Optional.of(bloodRequest));
            ApiException e = assertThrows(ApiException.class, () -> circleService.createOrGetCircle(100L, 10L), closed.name());
            assertEquals(HttpStatus.CONFLICT, e.getStatus());
        }
    }

    // ---------------------------------------------------------------- public info

    @Test
    @DisplayName("Lookup is by hash of the presented token and returns safe public info")
    void getCircleInfo_returnsSafeInfo() {
        when(requestCircleRepository.findByInviteTokenHash(tokens.hash(token))).thenReturn(Optional.of(circle));

        CircleInfoDto info = circleService.getCircleInfo(token);

        assertEquals("B+", info.getPatientBloodType());
        assertEquals("Kandy General Hospital", info.getHospitalName());
        assertEquals(Urgency.CRITICAL, info.getUrgency());
        assertFalse(info.isExpired());
    }

    @Test
    @DisplayName("Unknown token: not found, and the message does not contain the token")
    void getCircleInfo_unknownToken_doesNotEchoToken() {
        String bogus = "bogus-token-ABCDEF123456";
        when(requestCircleRepository.findByInviteTokenHash(tokens.hash(bogus))).thenReturn(Optional.empty());

        CircleNotFoundException e = assertThrows(CircleNotFoundException.class, () -> circleService.getCircleInfo(bogus));
        assertFalse(e.getMessage().contains(bogus));
    }

    @Test
    @DisplayName("Expired token: gone, and the message does not contain the token")
    void getCircleInfo_expired_doesNotEchoToken() {
        circle.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(requestCircleRepository.findByInviteTokenHash(tokens.hash(token))).thenReturn(Optional.of(circle));

        CircleExpiredException e = assertThrows(CircleExpiredException.class, () -> circleService.getCircleInfo(token));
        assertFalse(e.getMessage().contains(token));
    }

    @Test
    @DisplayName("The token stops working as soon as the request is cancelled, fulfilled or expired")
    void tokenInvalidWhenRequestClosed() {
        when(requestCircleRepository.findByInviteTokenHash(tokens.hash(token))).thenReturn(Optional.of(circle));
        for (RequestStatus closed : new RequestStatus[] {RequestStatus.CANCELLED, RequestStatus.FULFILLED, RequestStatus.EXPIRED}) {
            bloodRequest.setStatus(closed);
            CircleExpiredException e = assertThrows(CircleExpiredException.class, () -> circleService.getCircleInfo(token), closed.name());
            assertFalse(e.getMessage().contains(token));
        }
        for (RequestStatus open : new RequestStatus[] {RequestStatus.OPEN, RequestStatus.ESCALATING, RequestStatus.MATCHED}) {
            bloodRequest.setStatus(open);
            assertNotNull(circleService.getCircleInfo(token), open.name());
        }
    }

    @Test
    @DisplayName("Pledging to a closed request is refused and nothing is saved")
    void submitResponse_closedRequest_refused() {
        bloodRequest.setStatus(RequestStatus.CANCELLED);
        when(requestCircleRepository.findByInviteTokenHashForUpdate(tokens.hash(token))).thenReturn(Optional.of(circle));

        assertThrows(CircleExpiredException.class, () -> circleService.submitResponse(token, pledge("0771234567")));
        verify(circleResponseRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- pledges

    private SubmitCircleResponseDto pledge(String phone) {
        return new SubmitCircleResponseDto("Kasun Perera", phone, "B+", "Available in 1 hour");
    }

    private void stubValidCircle() {
        when(requestCircleRepository.findByInviteTokenHashForUpdate(tokens.hash(token))).thenReturn(Optional.of(circle));
    }

    @Test
    @DisplayName("A pledge is stored with a normalised phone number")
    void submitResponse_recordsVolunteer() {
        stubValidCircle();
        when(circleResponseRepository.save(any(CircleResponse.class))).thenAnswer(inv -> {
            CircleResponse r = inv.getArgument(0);
            r.setId(10L);
            return r;
        });

        CircleResponseDto response = circleService.submitResponse(token, pledge(" 077 123 4567 "));

        assertEquals("Kasun Perera", response.getResponderName());
        assertEquals("+94771234567", response.getResponderPhone());
        ArgumentCaptor<CircleResponse> captor = ArgumentCaptor.forClass(CircleResponse.class);
        verify(circleResponseRepository).save(captor.capture());
        assertEquals("+94771234567", captor.getValue().getResponderPhone());
    }

    @Test
    @DisplayName("Invalid phone numbers are rejected with 400 before anything is stored")
    void submitResponse_invalidPhone() {
        stubValidCircle();
        for (String bad : new String[] {"12345", "0000000000", "07712345", "abcdefghij", "+0771234567", "0771234567890123456"}) {
            ApiException e = assertThrows(ApiException.class, () -> circleService.submitResponse(token, pledge(bad)), bad);
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), bad);
        }
        verify(circleResponseRepository, never()).save(any());
    }

    @Test
    @DisplayName("A circle accepts at most the configured number of pledges")
    void submitResponse_circleCap() {
        stubValidCircle();
        when(circleResponseRepository.countByCircleId(1L)).thenReturn((long) properties.getMaxPledgesPerCircle());

        ApiException e = assertThrows(ApiException.class, () -> circleService.submitResponse(token, pledge("0771234567")));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(circleResponseRepository, never()).save(any());
    }

    @Test
    @DisplayName("The same phone number cannot pledge twice to one circle, whatever the notation")
    void submitResponse_phoneCapPerCircle() {
        stubValidCircle();
        when(circleResponseRepository.countByCircleIdAndResponderPhone(1L, "+94771234567")).thenReturn(1L);

        ApiException e = assertThrows(ApiException.class, () -> circleService.submitResponse(token, pledge("0771234567")));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(circleResponseRepository, never()).save(any());
    }

    @Test
    @DisplayName("One phone number is limited to a few pledges per day across circles")
    void submitResponse_phoneCapPerDay() {
        stubValidCircle();
        when(circleResponseRepository.countByResponderPhoneAndRespondedAtAfter(eq("+94771234567"), any(LocalDateTime.class)))
                .thenReturn((long) properties.getMaxPledgesPerPhonePerDay());

        ApiException e = assertThrows(ApiException.class, () -> circleService.submitResponse(token, pledge("+94771234567")));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
    }

    // ---------------------------------------------------------------- notification honesty

    @Test
    @DisplayName("requesterNotified is true only when a notifier really created a notification")
    void submitResponse_notificationFlag() {
        stubValidCircle();
        when(circleResponseRepository.save(any(CircleResponse.class))).thenAnswer(inv -> inv.getArgument(0));
        when(circleResponseRepository.countByCircleId(1L)).thenReturn(1L);

        // a notifier that reports "nothing sent" (the logging fallback) => not notified
        CircleService quiet = new CircleService(requestCircleRepository, circleResponseRepository, requestRepository,
                tokens, properties, List.of(new LoggingPledgeNotifier()));
        assertFalse(quiet.submitResponse(token, pledge("0771234567")).getRequesterNotified());

        // a notifier that creates a notification => notified, and it receives no phone number
        List<String> calls = new ArrayList<>();
        PledgeNotifier real = (rid, uid, name, blood, count) -> { calls.add(rid + "/" + uid + "/" + name + "/" + blood + "/" + count); return true; };
        CircleService loud = new CircleService(requestCircleRepository, circleResponseRepository, requestRepository,
                tokens, properties, List.of(real, new LoggingPledgeNotifier()));
        assertTrue(loud.submitResponse(token, pledge("0772222222")).getRequesterNotified());
        assertEquals(List.of("100/10/Kasun Perera/B+/1"), calls);

        // a notifier that blows up must not break the pledge
        PledgeNotifier broken = (rid, uid, name, blood, count) -> { throw new IllegalStateException("inbox down"); };
        CircleService resilient = new CircleService(requestCircleRepository, circleResponseRepository, requestRepository,
                tokens, properties, List.of(broken));
        assertFalse(resilient.submitResponse(token, pledge("0773333333")).getRequesterNotified());
    }

    @Test
    @DisplayName("No notifier at all: the pledge is saved, requesterNotified is false")
    void submitResponse_noNotifier() {
        stubValidCircle();
        when(circleResponseRepository.save(any(CircleResponse.class))).thenAnswer(inv -> inv.getArgument(0));
        CircleService none = new CircleService(requestCircleRepository, circleResponseRepository, requestRepository,
                tokens, properties, List.of());
        assertFalse(none.submitResponse(token, pledge("0771234567")).getRequesterNotified());
    }

    // ---------------------------------------------------------------- owner view

    @Test
    @DisplayName("Circle responses are returned only to the owning requester")
    void getCircleResponses_returnsListForOwner() {
        when(requestRepository.findById(100L)).thenReturn(Optional.of(bloodRequest));
        when(requestCircleRepository.findByRequestId(100L)).thenReturn(Optional.of(circle));
        CircleResponse resp1 = new CircleResponse(circle, "Volunteer 1", "+94771111111", "B+", "Can come immediately");
        resp1.setId(1L);
        when(circleResponseRepository.findByCircleIdOrderByRespondedAtDesc(circle.getId())).thenReturn(List.of(resp1));

        List<CircleResponseDto> responses = circleService.getCircleResponses(100L, 10L);

        assertEquals(1, responses.size());
        assertEquals("Volunteer 1", responses.get(0).getResponderName());
        assertNull(responses.get(0).getRequesterNotified());
        assertThrows(AccessDeniedException.class, () -> circleService.getCircleResponses(100L, 999L));
    }
}
