package com.suwasarana.api.admin;

import com.suwasarana.api.request.RequestReport;
import com.suwasarana.api.request.RequestReportRepository;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestReportRepository requestReportRepository;

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
        // Mock data for analytics
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("Colombo", 150);
        analytics.put("Gampaha", 90);
        analytics.put("Jaffna", 15);
        analytics.put("Kandy", 60);
        return analytics;
    }
}
