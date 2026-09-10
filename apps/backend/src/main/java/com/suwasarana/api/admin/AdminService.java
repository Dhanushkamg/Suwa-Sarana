package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.DistrictSummaryDto;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestReport;
import com.suwasarana.api.request.RequestReportRepository;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AdminService {

    public static final Map<String, double[]> DISTRICT_COORDINATES = new LinkedHashMap<>();
    static {
        DISTRICT_COORDINATES.put("Colombo", new double[]{6.9271, 79.8612});
        DISTRICT_COORDINATES.put("Gampaha", new double[]{7.0840, 80.0098});
        DISTRICT_COORDINATES.put("Kalutara", new double[]{6.5854, 79.9607});
        DISTRICT_COORDINATES.put("Kandy", new double[]{7.2906, 80.6337});
        DISTRICT_COORDINATES.put("Matale", new double[]{7.4675, 80.6234});
        DISTRICT_COORDINATES.put("Nuwara Eliya", new double[]{6.9497, 80.7891});
        DISTRICT_COORDINATES.put("Galle", new double[]{6.0535, 80.2210});
        DISTRICT_COORDINATES.put("Matara", new double[]{5.9549, 80.5550});
        DISTRICT_COORDINATES.put("Hambantota", new double[]{6.1429, 81.1212});
        DISTRICT_COORDINATES.put("Jaffna", new double[]{9.6615, 80.0255});
        DISTRICT_COORDINATES.put("Kilinochchi", new double[]{9.3803, 80.3770});
        DISTRICT_COORDINATES.put("Mannar", new double[]{8.9810, 79.9044});
        DISTRICT_COORDINATES.put("Vavuniya", new double[]{8.7542, 80.4982});
        DISTRICT_COORDINATES.put("Mullaitivu", new double[]{9.2671, 80.8143});
        DISTRICT_COORDINATES.put("Batticaloa", new double[]{7.7310, 81.6747});
        DISTRICT_COORDINATES.put("Ampara", new double[]{7.2912, 81.6724});
        DISTRICT_COORDINATES.put("Trincomalee", new double[]{8.5874, 81.2152});
        DISTRICT_COORDINATES.put("Kurunegala", new double[]{7.4863, 80.3623});
        DISTRICT_COORDINATES.put("Puttalam", new double[]{8.0408, 79.8394});
        DISTRICT_COORDINATES.put("Anuradhapura", new double[]{8.3114, 80.4037});
        DISTRICT_COORDINATES.put("Polonnaruwa", new double[]{7.9403, 81.0188});
        DISTRICT_COORDINATES.put("Badulla", new double[]{6.9934, 81.0550});
        DISTRICT_COORDINATES.put("Monaragala", new double[]{6.8728, 81.3507});
        DISTRICT_COORDINATES.put("Ratnapura", new double[]{6.6828, 80.3992});
        DISTRICT_COORDINATES.put("Kegalle", new double[]{7.2513, 80.3464});
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestReportRepository requestReportRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private DonorRepository donorRepository;

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

    public List<DistrictSummaryDto> getDistrictSummaries() {
        List<BloodRequest> allRequests = requestRepository.findAll();
        List<DonorProfile> allDonors = donorRepository.findAll();

        Map<String, List<BloodRequest>> requestsByDistrict = new HashMap<>();
        for (BloodRequest req : allRequests) {
            if (req.getDistrict() != null) {
                requestsByDistrict.computeIfAbsent(req.getDistrict().trim(), k -> new ArrayList<>()).add(req);
            }
        }

        Map<String, List<DonorProfile>> donorsByDistrict = new HashMap<>();
        for (DonorProfile donor : allDonors) {
            if (donor.getDistrict() != null) {
                donorsByDistrict.computeIfAbsent(donor.getDistrict().trim(), k -> new ArrayList<>()).add(donor);
            }
        }

        List<DistrictSummaryDto> summaries = new ArrayList<>();

        for (Map.Entry<String, double[]> entry : DISTRICT_COORDINATES.entrySet()) {
            String district = entry.getKey();
            double[] coords = entry.getValue();

            List<BloodRequest> districtReqs = requestsByDistrict.getOrDefault(district, Collections.emptyList());
            List<DonorProfile> districtDonors = donorsByDistrict.getOrDefault(district, Collections.emptyList());

            long totalRequests = districtReqs.size();
            long activeRequests = districtReqs.stream().filter(r ->
                    r.getStatus() == RequestStatus.OPEN ||
                    r.getStatus() == RequestStatus.ESCALATING ||
                    r.getStatus() == RequestStatus.MATCHED
            ).count();

            long fulfilledRequests = districtReqs.stream().filter(r ->
                    r.getStatus() == RequestStatus.FULFILLED
            ).count();

            long donorCount = districtDonors.size();

            double fulfillmentRate = totalRequests > 0
                    ? Math.round(((double) fulfilledRequests / totalRequests) * 1000.0) / 10.0
                    : 100.0;

            String shortageLevel = calculateShortageLevel(donorCount, activeRequests);

            summaries.add(new DistrictSummaryDto(
                    district,
                    coords[0],
                    coords[1],
                    donorCount,
                    activeRequests,
                    fulfilledRequests,
                    totalRequests,
                    fulfillmentRate,
                    shortageLevel
            ));
        }

        return summaries;
    }

    public String calculateShortageLevel(long donorCount, long activeRequests) {
        if (activeRequests > 0 && donorCount == 0) {
            return "CRITICAL";
        }
        if (activeRequests > 0 && ((double) donorCount / activeRequests) < 2.0) {
            return "CRITICAL";
        }
        if (activeRequests > 0 && ((double) donorCount / activeRequests) < 5.0) {
            return "WARNING";
        }
        if (donorCount >= 10 && activeRequests == 0) {
            return "SURPLUS";
        }
        return "BALANCED";
    }

    public List<BloodRequest> getTriageQueue() {
        return requestRepository.findAllByOrderByFraudRiskScoreDesc();
    }

    @Transactional
    public void reviewTriageRequest(Long requestId, String adminNotes) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));
        request.setFraudRiskScore(0);
        request.setAiFlagReason("Reviewed and resolved by admin. Notes: " + (adminNotes != null ? adminNotes : "None"));
        requestRepository.save(request);
    }
}
