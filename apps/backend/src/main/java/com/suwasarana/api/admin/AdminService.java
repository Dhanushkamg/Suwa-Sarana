package com.suwasarana.api.admin;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestReport;
import com.suwasarana.api.request.RequestReportRepository;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestReportRepository requestReportRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Transactional
    public void verifyUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        userRepository.save(user);
    }

    public List<RequestReport> getReports() {
        return requestReportRepository.findAll();
    }

    public Map<String, Object> getDistrictAnalytics() {
        List<BloodRequest> requests = requestRepository.findAll();
        Map<String, Object> analytics = new HashMap<>();

        if (requests.isEmpty()) {
            // Baseline seed display if no requests created yet
            analytics.put("Colombo", 12);
            analytics.put("Gampaha", 8);
            analytics.put("Kandy", 5);
            analytics.put("Galle", 3);
            return analytics;
        }

        for (BloodRequest request : requests) {
            String district = request.getDistrict() != null ? request.getDistrict() : "Unknown";
            analytics.put(district, (Integer) analytics.getOrDefault(district, 0) + 1);
        }

        return analytics;
    }
}
