package com.suwasarana.api.donor;

import com.suwasarana.api.testsupport.LifecycleIntegrationBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The donor profile validates the weight with the same facts (EligibilityFacts, strictly above 50 kg) as the eligibility rules. */
class DonorWeightValidationIntegrationTest extends LifecycleIntegrationBase {

    private static String profile(String weight) {
        return "{\"bloodType\":\"O+\",\"dateOfBirth\":\"1995-05-05\",\"weightKg\":" + weight
                + ",\"district\":\"Colombo\",\"latitude\":6.9271,\"longitude\":79.8612}";
    }

    @Test
    @DisplayName("exactly 50 kg is rejected (400 validation.failed), 50.01 kg is accepted")
    void weightMustBeStrictlyAboveTheKnowledgeBaseMinimum() throws Exception {
        User donor = user("weightdonor", Role.DONOR, VerificationStatus.VERIFIED);

        mvc.perform(put("/api/donors/me").with(as(donor)).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON).content(profile("50.0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.data.weightKg", containsString("above 50 kg")));

        mvc.perform(put("/api/donors/me").with(as(donor)).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON).content(profile("49")))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/donors/me").with(as(donor)).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON).content(profile("50.01")))
                .andExpect(status().isOk());
    }
}
