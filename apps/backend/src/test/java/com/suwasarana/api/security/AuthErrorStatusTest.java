package com.suwasarana.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Client mistakes must come back as 4xx (not 500), and a missing/expired JWT as 401 so the UI can refresh it. */
@SpringBootTest
@ActiveProfiles("test")
class AuthErrorStatusTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("Protected endpoint without a token answers 401")
    void protectedEndpoint_withoutToken_is401() throws Exception {
        mvc().perform(get("/api/donors/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh without a cookie answers 401")
    void refresh_withoutCookie_is401() throws Exception {
        mvc().perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Malformed JSON answers 400")
    void malformedJson_is400() throws Exception {
        mvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Public sign-up cannot create an ADMIN")
    void register_asAdmin_is400() throws Exception {
        String body = """
                {"email":"x@test.com","phoneNumber":"0771234567","password":"password123",
                 "firstName":"A","lastName":"B","role":"ADMIN","otpCode":"123456"}""";
        mvc().perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Wrong OTP answers 400")
    void verifyOtp_wrongCode_is400() throws Exception {
        String body = "{\"phoneNumber\":\"0771234567\",\"otpCode\":\"000000\"}";
        mvc().perform(post("/api/auth/verify-otp").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
