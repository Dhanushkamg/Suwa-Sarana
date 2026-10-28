package com.suwasarana.api.ivr;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class IvrControllerTest {

    @Mock
    private RequestRepository requestRepository;

    private IvrProperties properties;
    private IvrFacts facts;
    private IvrController controller;

    @BeforeEach
    void setUp() {
        properties = new IvrProperties();
        properties.setSignatureRequired(false); // signature handling has its own tests
        facts = new IvrFacts();
        controller = new IvrController(new IvrService(requestRepository, properties, facts),
                new TwilioSignatureValidator(properties), properties);
    }

    private ResponseEntity<String> call(String digits) {
        return controller.handleTwilioWebhook(new MockHttpServletRequest("POST", "/api/ivr/webhook"), null, digits);
    }

    private static BloodRequest request(Urgency urgency, VerificationStatus requesterStatus) {
        User u = new User();
        u.setVerificationStatus(requesterStatus);
        BloodRequest r = new BloodRequest();
        r.setId(1L);
        r.setRequester(u);
        r.setPatientBloodType("O+");
        r.setUnitsNeeded((short) 2);
        r.setHospitalName("Colombo National Hospital");
        r.setDistrict("Colombo");
        r.setUrgency(urgency);
        r.setStatus(RequestStatus.OPEN);
        r.setExpiresAt(LocalDateTime.now().plusHours(6));
        r.setFraudRiskScore(0);
        return r;
    }

    private void active(BloodRequest... requests) {
        lenient().when(requestRepository.findActiveRequests()).thenReturn(List.of(requests));
    }

    @Test
    @DisplayName("No digit pressed: the menu")
    void greetingMenu() {
        ResponseEntity<String> response = call(null);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("<Gather numDigits=\"1\""));
        assertTrue(response.getBody().contains("Welcome to the Suwa Sarana Emergency Blood Hotline"));
    }

    @Test
    @DisplayName("1: a verified critical request is read out")
    void verifiedCriticalRequestIsRead() {
        active(request(Urgency.CRITICAL, VerificationStatus.VERIFIED));
        String body = call("1").getBody();
        assertTrue(body.contains("2 units of O positive blood at Colombo National Hospital in Colombo"), body);
        assertTrue(body.contains("<Hangup/>"));
    }

    @Test
    @DisplayName("1: requests of unverified or rejected requesters are never read out")
    void unverifiedRequestsAreNotRead() {
        active(request(Urgency.CRITICAL, VerificationStatus.PENDING), request(Urgency.CRITICAL, VerificationStatus.REJECTED));
        String body = call("1").getBody();
        assertTrue(body.contains("no verified critical blood requests"), body);
        assertFalse(body.contains("Colombo National Hospital"));
        assertFalse(body.contains("O positive"));
    }

    @Test
    @DisplayName("1: only verified ones are read when verified and unverified requests are mixed")
    void mixedRequests() {
        BloodRequest good = request(Urgency.CRITICAL, VerificationStatus.VERIFIED);
        BloodRequest bad = request(Urgency.CRITICAL, VerificationStatus.PENDING);
        bad.setHospitalName("Secret Hospital");
        active(bad, good);
        String body = call("1").getBody();
        assertTrue(body.contains("Colombo National Hospital"));
        assertFalse(body.contains("Secret Hospital"));
        assertTrue(body.contains("one verified critical request"));
    }

    @Test
    @DisplayName("1: non-critical, flagged, expired and non-active requests are skipped")
    void otherFilters() {
        BloodRequest urgent = request(Urgency.URGENT, VerificationStatus.VERIFIED);
        BloodRequest flagged = request(Urgency.CRITICAL, VerificationStatus.VERIFIED);
        flagged.setFraudRiskScore(80);
        BloodRequest expired = request(Urgency.CRITICAL, VerificationStatus.VERIFIED);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        BloodRequest matched = request(Urgency.CRITICAL, VerificationStatus.VERIFIED);
        matched.setStatus(RequestStatus.MATCHED);
        active(urgent, flagged, expired, matched);
        assertTrue(call("1").getBody().contains("no verified critical blood requests"));
    }

    @Test
    @DisplayName("1: no requests at all")
    void noRequests() {
        lenient().when(requestRepository.findActiveRequests()).thenReturn(Collections.emptyList());
        assertTrue(call("1").getBody().contains("no verified critical blood requests"));
    }

    @Test
    @DisplayName("1: at most the configured number of requests, newest first")
    void limitAndOrder() {
        BloodRequest oldest = request(Urgency.CRITICAL, VerificationStatus.VERIFIED);
        oldest.setHospitalName("Oldest");
        oldest.setCreatedAt(LocalDateTime.now().minusHours(3));
        BloodRequest middle = request(Urgency.CRITICAL, VerificationStatus.VERIFIED);
        middle.setHospitalName("Middle");
        middle.setCreatedAt(LocalDateTime.now().minusHours(2));
        BloodRequest newest = request(Urgency.CRITICAL, VerificationStatus.VERIFIED);
        newest.setHospitalName("Newest");
        newest.setCreatedAt(LocalDateTime.now().minusHours(1));
        properties.setMaxCriticalRequests(2);
        active(oldest, newest, middle);
        String body = call("1").getBody();
        assertTrue(body.indexOf("Newest") > 0 && body.indexOf("Newest") < body.indexOf("Middle"), body);
        assertFalse(body.contains("Oldest"));
    }

    @Test
    @DisplayName("1: hospital names are XML-escaped so a hostile name cannot inject TwiML")
    void xmlIsEscaped() {
        BloodRequest r = request(Urgency.CRITICAL, VerificationStatus.VERIFIED);
        r.setHospitalName("</Say><Dial>+15551234567</Dial><Say>");
        active(r);
        String body = call("1").getBody();
        assertFalse(body.contains("<Dial>"), body);
        assertTrue(body.contains("&lt;/Say&gt;&lt;Dial&gt;"));
    }

    @Test
    @DisplayName("2: eligibility facts come from IvrFacts and match the NBTS-published values")
    void eligibilityFacts() {
        String body = call("2").getBody();
        assertTrue(body.contains("between 18 and 60 years old"), body);
        assertTrue(body.contains("first-time donors must not be older than 55"));
        assertTrue(body.contains("more than 50 kilograms"));
        assertTrue(body.contains("above 12.5 grams per decilitre"));
        assertTrue(body.contains("at least 4 months between donations"));
        assertFalse(body.contains("3 to 4 months"), "the old contradictory wording must be gone");
        assertFalse(body.contains("12 months"), "no unsourced tattoo/surgery figure may be spoken");
        assertTrue(body.contains("ask the medical officer"));
    }

    @Test
    @DisplayName("2: changing IvrFacts changes what is spoken; tattoo/surgery figures only when configured")
    void eligibilityFollowsConfiguration() {
        facts.setIntervalMonths(5);
        facts.setTattooMonths(9);
        String body = call("2").getBody();
        assertTrue(body.contains("at least 5 months"));
        assertTrue(body.contains("wait 9 months"));
        assertFalse(body.contains("After surgery"));
    }

    @Test
    @DisplayName("3: the coordinator number comes from configuration")
    void coordinatorNumberIsConfigurable() {
        assertTrue(call("3").getBody().contains("<Dial>+94112369931</Dial>"));
        properties.setCoordinatorNumber("+94112222222");
        assertTrue(call("3").getBody().contains("<Dial>+94112222222</Dial>"));
    }

    @Test
    @DisplayName("3: an invalid configured number is never dialled")
    void invalidCoordinatorNumber() {
        properties.setCoordinatorNumber("</Dial><Hangup/>");
        String body = call("3").getBody();
        assertFalse(body.contains("<Dial>"));
        assertTrue(body.contains("not available"));
    }

    @Test
    @DisplayName("Any other digit")
    void invalidDigit() {
        assertTrue(call("9").getBody().contains("Invalid selection"));
    }

    // ---------------------------------------------------------------- signature enforcement in the controller

    @Test
    @DisplayName("With signatures required, an unsigned or wrongly signed call gets 403 and no TwiML content")
    void unsignedCallIsRejected() {
        properties.setSignatureRequired(true);
        properties.setAuthToken("12345");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ivr/webhook");
        req.setParameter("Digits", "1");

        ResponseEntity<String> missing = controller.handleTwilioWebhook(req, null, "1");
        assertEquals(403, missing.getStatusCode().value());
        assertFalse(missing.getBody().contains("Say"));

        ResponseEntity<String> wrong = controller.handleTwilioWebhook(req, "bm90LXRoZS1zaWduYXR1cmU=", "1");
        assertEquals(403, wrong.getStatusCode().value());
        ResponseEntity<String> wrongGet = controller.handleTwilioWebhookGet(new MockHttpServletRequest("GET", "/api/ivr/webhook"), "x", "1");
        assertEquals(403, wrongGet.getStatusCode().value());
    }

    @Test
    @DisplayName("With signatures required but no auth token configured nothing is served")
    void noTokenMeansClosed() {
        properties.setSignatureRequired(true);
        properties.setAuthToken("");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ivr/webhook");
        String sig = TwilioSignatureValidator.sign("12345", "http://localhost/api/ivr/webhook");
        assertEquals(403, controller.handleTwilioWebhook(req, sig, null).getStatusCode().value());
    }

    @Test
    @DisplayName("A correctly signed call is served")
    void signedCallIsServed() {
        properties.setSignatureRequired(true);
        properties.setAuthToken("12345");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ivr/webhook");
        req.setParameter("Digits", "2");
        String sig = TwilioSignatureValidator.sign("12345", "http://localhost/api/ivr/webhookDigits2");
        ResponseEntity<String> ok = controller.handleTwilioWebhook(req, sig, "2");
        assertEquals(200, ok.getStatusCode().value());
        assertTrue(ok.getBody().contains("National Blood Transfusion Service"));
    }
}
