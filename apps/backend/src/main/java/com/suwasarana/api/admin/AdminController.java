package com.suwasarana.api.admin;

import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.request.RequestReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @PostMapping("/requesters/{id}/verify")
    public ResponseEntity<ApiResponse<Void>> verifyUser(@PathVariable Long id) {
        adminService.verifyUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "User verified successfully"));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<RequestReport>>> getReports() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getReports()));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDistrictAnalytics()));
    }
}
