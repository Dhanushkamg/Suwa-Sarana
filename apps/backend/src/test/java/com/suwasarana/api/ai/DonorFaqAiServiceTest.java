package com.suwasarana.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.ai.dto.FaqAnswerDto;
import com.suwasarana.api.ai.dto.FaqQuestionDto;
import com.suwasarana.api.donor.DeferralRulesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DonorFaqAiServiceTest {

    private DonorFaqAiService faqAiService;
    private DeferralRulesConfig deferralRulesConfig;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        deferralRulesConfig = new DeferralRulesConfig();
        faqAiService = new DonorFaqAiService(objectMapper, deferralRulesConfig);
        faqAiService.setGroqApiKey(""); // Test rule-grounded fallback engine
    }

    @Test
    @DisplayName("Should confirm system prompt dynamically contains live deferral rules from config")
    void testSystemPrompt_containsLiveDeferralRules() {
        String promptContext = deferralRulesConfig.generateSystemPromptContext();

        assertNotNull(promptContext);
        assertTrue(promptContext.contains("18 and 60"));
        assertTrue(promptContext.contains("50.00 kg"));
        assertTrue(promptContext.contains("120 days"));
        assertTrue(promptContext.contains("12 months"));
        assertTrue(promptContext.contains("CLINICAL SAFETY MANDATE"));
    }

    @Test
    @DisplayName("Should answer English tattoo question with 12 months deferral rule")
    void testAskQuestion_tattooEnglish() {
        FaqQuestionDto question = new FaqQuestionDto("Can I donate blood if I got a tattoo last month?", "en");
        FaqAnswerDto answer = faqAiService.askQuestion(question);

        assertNotNull(answer);
        assertTrue(answer.isGroundedInRules());
        assertTrue(answer.getAnswer().contains("12 months") || answer.getAnswer().contains("1 year"));
        assertNotNull(answer.getDisclaimer());
    }

    @Test
    @DisplayName("Should answer Sinhala tattoo question accurately")
    void testAskQuestion_tattooSinhala() {
        FaqQuestionDto question = new FaqQuestionDto("පච්චයක් (Tattoo) ගැහුවට පස්සේ ලේ දෙන්න පුලුවන්ද?", "si");
        FaqAnswerDto answer = faqAiService.askQuestion(question);

        assertNotNull(answer);
        assertTrue(answer.isGroundedInRules());
        assertTrue(answer.getAnswer().contains("12") || answer.getAnswer().contains("මාස"));
    }

    @Test
    @DisplayName("Should answer Tamil tattoo question accurately")
    void testAskQuestion_tattooTamil() {
        FaqQuestionDto question = new FaqQuestionDto("பச்சை குத்திய பிறகு நான் இரத்த தானம் செய்யலாமா?", "ta");
        FaqAnswerDto answer = faqAiService.askQuestion(question);

        assertNotNull(answer);
        assertTrue(answer.isGroundedInRules());
        assertTrue(answer.getAnswer().contains("12") || answer.getAnswer().contains("மாதங்கள்"));
    }

    @Test
    @DisplayName("Should answer age and weight criteria questions")
    void testAskQuestion_ageAndWeight() {
        FaqQuestionDto ageQuestion = new FaqQuestionDto("What is the eligible age range to donate blood?", "en");
        FaqAnswerDto ageAnswer = faqAiService.askQuestion(ageQuestion);
        assertTrue(ageAnswer.getAnswer().contains("18") && ageAnswer.getAnswer().contains("60"));

        FaqQuestionDto weightQuestion = new FaqQuestionDto("What is the minimum weight requirement?", "en");
        FaqAnswerDto weightAnswer = faqAiService.askQuestion(weightQuestion);
        assertTrue(weightAnswer.getAnswer().contains("50"));
    }

    @Test
    @DisplayName("Should handle empty query gracefully")
    void testAskQuestion_emptyInput() {
        FaqQuestionDto emptyQuestion = new FaqQuestionDto("", "en");
        FaqAnswerDto answer = faqAiService.askQuestion(emptyQuestion);

        assertNotNull(answer);
        assertFalse(answer.isGroundedInRules());
    }
}
