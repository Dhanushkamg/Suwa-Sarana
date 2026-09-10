package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.DistrictSummaryDto;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DistrictAnalyticsTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private DonorRepository donorRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminService adminService;

    private BloodRequest req1;
    private BloodRequest req2;
    private BloodRequest req3;
    private DonorProfile donor1;
    private DonorProfile donor2;

    @BeforeEach
    void setUp() {
        req1 = new BloodRequest();
        req1.setId(1L);
        req1.setDistrict("Colombo");
        req1.setStatus(RequestStatus.OPEN);

        req2 = new BloodRequest();
        req2.setId(2L);
        req2.setDistrict("Colombo");
        req2.setStatus(RequestStatus.FULFILLED);

        req3 = new BloodRequest();
        req3.setId(3L);
        req3.setDistrict("Kandy");
        req3.setStatus(RequestStatus.OPEN);

        donor1 = new DonorProfile();
        donor1.setId(1L);
        donor1.setDistrict("Colombo");

        donor2 = new DonorProfile();
        donor2.setId(2L);
        donor2.setDistrict("Gampaha");
    }

    @Test
    @DisplayName("Should return 25 district summaries for Sri Lanka")
    void testGetDistrictSummaries_returnsAll25Districts() {
        when(requestRepository.findAll()).thenReturn(Arrays.asList(req1, req2, req3));
        when(donorRepository.findAll()).thenReturn(Arrays.asList(donor1, donor2));

        List<DistrictSummaryDto> summaries = adminService.getDistrictSummaries();

        assertNotNull(summaries);
        assertEquals(25, summaries.size());

        DistrictSummaryDto colombo = summaries.stream()
                .filter(s -> "Colombo".equals(s.getDistrict()))
                .findFirst()
                .orElse(null);

        assertNotNull(colombo);
        assertEquals(1, colombo.getDonorCount());
        assertEquals(1, colombo.getActiveRequests());
        assertEquals(1, colombo.getFulfilledRequests());
        assertEquals(2, colombo.getTotalRequests());
        assertEquals(50.0, colombo.getFulfillmentRate());
        assertEquals("CRITICAL", colombo.getShortageLevel()); // 1 donor / 1 request = 1.0 < 2.0 -> CRITICAL
    }

    @Test
    @DisplayName("Should flag district with zero donors and active requests as CRITICAL")
    void testGetDistrictSummaries_flagsZeroDonorActiveAsCritical() {
        when(requestRepository.findAll()).thenReturn(Collections.singletonList(req3));
        when(donorRepository.findAll()).thenReturn(Collections.emptyList());

        List<DistrictSummaryDto> summaries = adminService.getDistrictSummaries();

        DistrictSummaryDto kandy = summaries.stream()
                .filter(s -> "Kandy".equals(s.getDistrict()))
                .findFirst()
                .orElse(null);

        assertNotNull(kandy);
        assertEquals(0, kandy.getDonorCount());
        assertEquals(1, kandy.getActiveRequests());
        assertEquals("CRITICAL", kandy.getShortageLevel());
    }

    @Test
    @DisplayName("Should calculate shortage levels correctly")
    void testCalculateShortageLevel() {
        assertEquals("CRITICAL", adminService.calculateShortageLevel(0, 5));
        assertEquals("CRITICAL", adminService.calculateShortageLevel(2, 2)); // ratio 1.0 < 2.0
        assertEquals("WARNING", adminService.calculateShortageLevel(3, 1));  // ratio 3.0 (2.0 - 5.0)
        assertEquals("BALANCED", adminService.calculateShortageLevel(8, 1)); // ratio 8.0 >= 5.0
        assertEquals("SURPLUS", adminService.calculateShortageLevel(12, 0)); // >= 10 donors and 0 active
        assertEquals("BALANCED", adminService.calculateShortageLevel(0, 0)); // 0 donors, 0 active
    }
}
