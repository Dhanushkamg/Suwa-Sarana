package com.suwasarana.api.ivr;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IvrControllerTest {

    @Mock
    private RequestRepository requestRepository;

    @InjectMocks
    private IvrController ivrController;

    @Test
    public void testInitialGreetingMenu_NoDigits() {
        ResponseEntity<String> response = ivrController.handleTwilioWebhook(null, "+94771234567");
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("<Gather numDigits=\"1\""));
        assertTrue(response.getBody().contains("Welcome to the Suwa Sarana Emergency Blood Hotline"));
    }

    @Test
    public void testDigits1_ActiveCriticalRequests() {
        BloodRequest req = new BloodRequest();
        req.setId(1L);
        req.setPatientBloodType("O+");
        req.setUnitsNeeded((short) 2);
        req.setHospitalName("Colombo National Hospital");
        req.setDistrict("Colombo");
        req.setUrgency(Urgency.CRITICAL);

        when(requestRepository.findActiveRequests()).thenReturn(List.of(req));

        ResponseEntity<String> response = ivrController.handleTwilioWebhook("1", "+94771234567");
        assertNotNull(response);
        assertTrue(response.getBody().contains("2 units of O positive blood at Colombo National Hospital"));
        assertTrue(response.getBody().contains("<Hangup/>"));
    }

    @Test
    public void testDigits1_NoCriticalRequests() {
        when(requestRepository.findActiveRequests()).thenReturn(Collections.emptyList());

        ResponseEntity<String> response = ivrController.handleTwilioWebhook("1", "+94771234567");
        assertNotNull(response);
        assertTrue(response.getBody().contains("no active critical blood requests"));
    }

    @Test
    public void testDigits2_EligibilityGuidelines() {
        ResponseEntity<String> response = ivrController.handleTwilioWebhook("2", "+94771234567");
        assertNotNull(response);
        assertTrue(response.getBody().contains("National Blood Transfusion Service guidelines"));
        assertTrue(response.getBody().contains("18 and 60 years of age"));
    }

    @Test
    public void testDigits3_CoordinatorTransfer() {
        ResponseEntity<String> response = ivrController.handleTwilioWebhook("3", "+94771234567");
        assertNotNull(response);
        assertTrue(response.getBody().contains("<Dial>+94112369931</Dial>"));
    }

    @Test
    public void testDigitsInvalid() {
        ResponseEntity<String> response = ivrController.handleTwilioWebhook("9", "+94771234567");
        assertNotNull(response);
        assertTrue(response.getBody().contains("Invalid selection"));
    }
}
