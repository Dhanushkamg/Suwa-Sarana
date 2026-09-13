package com.suwasarana.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ActuatorSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc buildMockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("GET /actuator/health is publicly accessible")
    void actuatorHealth_isPublic() throws Exception {
        buildMockMvc().perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /actuator/env requires auth")
    void actuatorEnv_requiresAuth() throws Exception {
        buildMockMvc().perform(get("/actuator/env")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /actuator/metrics requires auth")
    void actuatorMetrics_requiresAuth() throws Exception {
        buildMockMvc().perform(get("/actuator/metrics")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Swagger UI requires ADMIN auth")
    void swaggerUi_requiresAdminAuth() throws Exception {
        buildMockMvc().perform(get("/swagger-ui/index.html")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("OpenAPI docs require ADMIN auth")
    void openApiDocs_requiresAdminAuth() throws Exception {
        buildMockMvc().perform(get("/v3/api-docs")).andExpect(status().is4xxClientError());
    }
}
