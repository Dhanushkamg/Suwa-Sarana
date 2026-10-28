package com.suwasarana.api.ivr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The webhook through the real security chain: it is public, but only for callers that can sign. */
@SpringBootTest(properties = {"app.ivr.auth-token=test-twilio-token", "app.ivr.signature-required=true"})
@ActiveProfiles("test")
class IvrHttpTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("Unsigned POST and GET are 403")
    void unsigned() throws Exception {
        mvc.perform(post("/api/ivr/webhook").param("Digits", "1")).andExpect(status().isForbidden());
        mvc.perform(get("/api/ivr/webhook").param("Digits", "1")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A request with a forged signature is 403")
    void forged() throws Exception {
        mvc.perform(post("/api/ivr/webhook").param("Digits", "1").header("X-Twilio-Signature", "Zm9yZ2Vk"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A correctly signed POST gets TwiML")
    void signedPost() throws Exception {
        String sig = TwilioSignatureValidator.sign("test-twilio-token", "http://localhost/api/ivr/webhookDigits2");
        mvc.perform(post("/api/ivr/webhook").param("Digits", "2").header("X-Twilio-Signature", sig))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<Say")))
                .andExpect(content().string(containsString("at least 4 months")));
    }

    @Test
    @DisplayName("A correctly signed GET with a query string gets TwiML")
    void signedGet() throws Exception {
        String sig = TwilioSignatureValidator.sign("test-twilio-token", "http://localhost/api/ivr/webhook?Digits=3");
        mvc.perform(get("/api/ivr/webhook?Digits=3").header("X-Twilio-Signature", sig))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<Dial>+94112369931</Dial>")));
    }
}
