package com.suwasarana.api.camp;

import com.suwasarana.api.camp.dto.CreateCampDto;
import com.suwasarana.api.camp.dto.DonationCampDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/camps")
public class DonationCampController {

    @Autowired
    private DonationCampService campService;

    @GetMapping
    public ResponseEntity<List<DonationCampDto>> getUpcomingCamps() {
        return ResponseEntity.ok(campService.getUpcomingCamps());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'ADMIN')")
    public ResponseEntity<DonationCampDto> createCamp(@Valid @RequestBody CreateCampDto dto, Authentication auth) {
        return ResponseEntity.ok(campService.createCamp(dto, auth.getName()));
    }

    @PostMapping("/{id}/register")
    @PreAuthorize("hasRole('DONOR')")
    public ResponseEntity<?> registerForCamp(@PathVariable Long id, Authentication auth) {
        campService.registerForCamp(id, auth.getName());
        return ResponseEntity.ok(Map.of("message", "Registered successfully"));
    }
}
