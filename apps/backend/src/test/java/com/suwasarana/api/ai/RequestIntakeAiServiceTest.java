package com.suwasarana.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.ai.dto.AiDraftRequestDto;
import com.suwasarana.api.ai.dto.AiDraftResponseDto;
import com.suwasarana.api.request.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RequestIntakeAiServiceTest {

    private RequestIntakeAiService aiService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        aiService = new RequestIntakeAiService(objectMapper);
        aiService.setGroqApiKey(""); // Test fallback rule-based parsing engine directly
    }

    @Test
    @DisplayName("Should extract English emergency requisition details accurately")
    void testExtractDraft_EnglishInput() {
        AiDraftRequestDto request = new AiDraftRequestDto(
                "Urgent need for 2 units of O+ blood at Karapitiya Teaching Hospital in Galle for emergency surgery",
                "en"
        );

        AiDraftResponseDto result = aiService.extractDraft(request);

        assertNotNull(result);
        assertTrue(result.isExtractedSuccessfully());
        assertEquals("O+", result.getPatientBloodType());
        assertEquals((short) 2, result.getUnitsNeeded());
        assertEquals("Galle", result.getDistrict());
        assertEquals(Urgency.CRITICAL, result.getUrgency());
        assertEquals(6.0535, result.getLatitude(), 0.0001);
        assertEquals(80.2210, result.getLongitude(), 0.0001);
    }

    @Test
    @DisplayName("Should extract Sinhala emergency requisition details accurately")
    void testExtractDraft_SinhalaInput() {
        AiDraftRequestDto request = new AiDraftRequestDto(
                "ගාල්ල කරාපිටිය රෝහලේ හදිසි සැත්කමක් සඳහා B- ලේ යුනිට් 3ක් අවශ්‍යයි",
                "si"
        );

        AiDraftResponseDto result = aiService.extractDraft(request);

        assertNotNull(result);
        assertTrue(result.isExtractedSuccessfully());
        assertEquals("B-", result.getPatientBloodType());
        assertEquals((short) 3, result.getUnitsNeeded());
        assertEquals(Urgency.CRITICAL, result.getUrgency());
    }

    @Test
    @DisplayName("Should extract Tamil emergency requisition details accurately")
    void testExtractDraft_TamilInput() {
        AiDraftRequestDto request = new AiDraftRequestDto(
                "அவசர சிகிச்சைக்காக 1 யூனிட் AB+ இரத்தம் தேவை",
                "ta"
        );

        AiDraftResponseDto result = aiService.extractDraft(request);

        assertNotNull(result);
        assertTrue(result.isExtractedSuccessfully());
        assertEquals("AB+", result.getPatientBloodType());
        assertEquals((short) 1, result.getUnitsNeeded());
        assertEquals(Urgency.CRITICAL, result.getUrgency());
    }

    @Test
    @DisplayName("Should reject empty text prompt gracefully")
    void testExtractDraft_emptyInput() {
        AiDraftRequestDto request = new AiDraftRequestDto("", "en");
        AiDraftResponseDto result = aiService.extractDraft(request);

        assertNotNull(result);
        assertFalse(result.isExtractedSuccessfully());
        assertEquals("Input text cannot be empty.", result.getMessage());
    }
}
