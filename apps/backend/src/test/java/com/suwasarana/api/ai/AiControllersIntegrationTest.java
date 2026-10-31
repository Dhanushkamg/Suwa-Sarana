package com.suwasarana.api.ai;

import com.suwasarana.api.auth.AuthService;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.auth.otp.OtpVerification;
import com.suwasarana.api.auth.otp.OtpVerificationRepository;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level checks of the AI endpoints with the real Spring context (H2). */
@SpringBootTest
@ActiveProfiles("test")
class AiControllersIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OtpVerificationRepository otpRepository;

    private MockMvc mvc;
    private String pendingToken;
    private String verifiedToken;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        pendingToken = register(false);
        verifiedToken = register(true);
        // registering authenticates the test thread; the unauthenticated checks below need a clean context
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
    }

    private String register(boolean verified) {
        String phone = "0779" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        OtpVerification otp = new OtpVerification();
        otp.setPhoneNumber(phone);
        otp.setOtpCode("123456");
        otp.setVerified(true);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        otpRepository.save(otp);
        RegisterDto dto = new RegisterDto();
        dto.setEmail("ai_" + UUID.randomUUID() + "@test.com");
        dto.setPhoneNumber(phone);
        dto.setFirstName("Ai");
        dto.setLastName("Tester");
        dto.setPassword("password12");
        dto.setRole(Role.REQUESTER);
        AuthResponse auth = authService.register(dto, "127.0.0.1");
        if (verified) {
            User u = userRepository.findById(auth.getUserId()).orElseThrow();
            u.setVerificationStatus(VerificationStatus.VERIFIED);
            userRepository.save(u);
        }
        return auth.getAccessToken();
    }

    @Test
    @DisplayName("POST /api/faq/ask is public and answers both parts with sources, grounded and the deprecated flag")
    void faqAsk() throws Exception {
        mvc.perform(post("/api/faq/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is the minimum age and weight to donate blood?\",\"language\":\"auto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.grounded").value(true))
                .andExpect(jsonPath("$.data.groundedInRules").value(true))
                .andExpect(jsonPath("$.data.language").value("en"))
                .andExpect(jsonPath("$.data.usedLlm").value(false))
                .andExpect(jsonPath("$.data.needsConfirmation").value(true))
                .andExpect(jsonPath("$.data.answer", containsString("50 kg")))
                .andExpect(jsonPath("$.data.answer", containsString("18")))
                .andExpect(jsonPath("$.data.sources[*].id", hasItem("ELIG-WEIGHT")))
                .andExpect(jsonPath("$.data.sources[*].id", hasItem("ELIG-AGE")))
                .andExpect(jsonPath("$.data.disclaimer", containsString("Medical Officer")));
        // old clients send {question, locale}
        mvc.perform(post("/api/faq/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Who won the cricket match?\",\"locale\":\"si\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("OFF_TOPIC"))
                .andExpect(jsonPath("$.data.grounded").value(false))
                .andExpect(jsonPath("$.data.sources").isEmpty());
    }

    @Test
    @DisplayName("faq validation: blank, over 500 characters, bad language and too long history are 400")
    void faqValidation() throws Exception {
        mvc.perform(post("/api/faq/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/faq/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + "a".repeat(501) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/faq/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"age\",\"language\":\"fr\"}")).andExpect(status().isBadRequest());
        StringBuilder h = new StringBuilder("[");
        for (int i = 0; i < 21; i++) h.append(i > 0 ? "," : "").append("{\"role\":\"user\",\"content\":\"x\"}");
        h.append("]");
        mvc.perform(post("/api/faq/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"age\",\"history\":" + h + "}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/faq/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"and women?\",\"history\":[{\"role\":\"user\",\"content\":\"How often can I donate?\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sources[*].id", hasItem("ELIG-INTERVAL")));
    }

    @Test
    @DisplayName("POST /api/ai/poster-copy: 401 without a token, 422 poster.unverified_requester, 422 for patient fields, 200 for a verified user")
    void posterCopy() throws Exception {
        String urgent = "{\"template\":\"URGENT_REQUEST\",\"language\":\"en\",\"facts\":{\"bloodType\":\"O+\",\"units\":\"2\",\"hospital\":\"Karapitiya Teaching Hospital\",\"district\":\"Galle\",\"contactPhone\":\"0771234567\"}}";
        mvc.perform(post("/api/ai/poster-copy").contentType(MediaType.APPLICATION_JSON).content(urgent)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/poster-copy").header("Authorization", "Bearer " + pendingToken).contentType(MediaType.APPLICATION_JSON).content(urgent))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("poster.unverified_requester"))
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/api/ai/poster-copy").header("Authorization", "Bearer " + verifiedToken).contentType(MediaType.APPLICATION_JSON).content(urgent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").value("URGENT: O+ blood needed"))
                .andExpect(jsonPath("$.data.usedLlm").value(false))
                .andExpect(jsonPath("$.data.hashtags[*]", hasItem("#Galle")));
        String withPatient = urgent.replace("\"facts\":{", "\"facts\":{\"patientName\":\"Nimal\",");
        mvc.perform(post("/api/ai/poster-copy").header("Authorization", "Bearer " + verifiedToken).contentType(MediaType.APPLICATION_JSON).content(withPatient))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("poster.patient_name_not_allowed"));
        mvc.perform(post("/api/ai/poster-copy").header("Authorization", "Bearer " + pendingToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template\":\"AWARENESS\",\"language\":\"si\",\"facts\":{}}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/ai/poster-copy").header("Authorization", "Bearer " + verifiedToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"template\":\"NOPE\",\"language\":\"en\",\"facts\":{}}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/requests/ai-draft keeps working and never invents a district")
    void aiDraft() throws Exception {
        mvc.perform(post("/api/requests/ai-draft").header("Authorization", "Bearer " + verifiedToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Need 2 units of O+ blood urgently somewhere\",\"locale\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientBloodType").value("O+"))
                .andExpect(jsonPath("$.data.district").value(nullValue()))
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.missingFields", hasItem("district")))
                .andExpect(jsonPath("$.data.needsConfirmation").exists());
        mvc.perform(post("/api/requests/ai-draft").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }
}
