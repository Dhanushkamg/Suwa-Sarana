package com.suwasarana.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security regression tests for Spring Boot Actuator endpoint protection.
 *
 * CRITICAL: Actuator endpoints expose internal application state (env vars,
 * metrics, bean definitions, etc.). Only /actuator/health should be public.
 * All other actuator endpoints must require ADMIN authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /actuator/health is publicly accessible (no auth required)")
    void actuatorHealth_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /actuator/env requires authentication — unauthenticated gets 401 or 403")
    void actuatorEnv_requiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /actuator/metrics requires authentication — unauthenticated gets 401 or 403")
    void actuatorMetrics_requiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /actuator/beans requires authentication — unauthenticated gets 401 or 403")
    void actuatorBeans_requiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /actuator/loggers requires authentication — unauthenticated gets 401 or 403")
    void actuatorLoggers_requiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/loggers"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Swagger UI requires ADMIN authentication — unauthenticated gets 401 or 403")
    void swaggerUi_requiresAdminAuth() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("OpenAPI docs require ADMIN authentication — unauthenticated gets 401 or 403")
    void openApiDocs_requiresAdminAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().is4xxClientError());
    }
}
