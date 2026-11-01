package com.suwasarana.api.request;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.suwasarana.api.ai.RequestTriageAiService;
import com.suwasarana.api.testsupport.LifecycleIntegrationBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A failing triage never blocks request creation, but it is logged with the class name and request id only (no PII). */
class TriageFailureLoggingIntegrationTest extends LifecycleIntegrationBase {

    @MockitoBean private RequestTriageAiService triage;

    @Test
    @DisplayName("createRequest still succeeds when triage throws, and logs an ERROR with the request id and exception class only")
    void triageFailureIsLogged() throws Exception {
        when(triage.triageRequest(any())).thenThrow(new IllegalStateException("secret-patient-detail 0771234567"));
        Logger logger = (Logger) LoggerFactory.getLogger(RequestService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            User owner = user("logreq", Role.REQUESTER, VerificationStatus.VERIFIED);
            mvc.perform(post("/api/requests").with(as(owner)).with(fromNewClient()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"patientBloodType\":\"A+\",\"unitsNeeded\":1,\"urgency\":\"URGENT\",\"hospitalName\":\"Base Hospital Colombo\","
                                    + "\"district\":\"Colombo\",\"latitude\":6.93,\"longitude\":79.86}"))
                    .andExpect(status().isOk());
            var errors = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
            assertEquals(1, errors.size(), "exactly one ERROR line expected");
            String line = errors.get(0).getFormattedMessage();
            assertTrue(line.contains("IllegalStateException"), line);
            assertFalse(line.contains("secret-patient-detail") || line.contains("0771234567"), "no exception message / PII in the log: " + line);
            assertTrue(line.matches(".*request \\d+.*"), line);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
