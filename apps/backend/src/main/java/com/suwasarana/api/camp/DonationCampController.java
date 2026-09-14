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

    @GetMapping("/recommended")
    @PreAuthorize("hasRole('DONOR')")
    public ResponseEntity<List<DonationCampDto>> getRecommendedCamps(Authentication auth) {
        return ResponseEntity.ok(campService.getRecommendedCamps(auth.getName()));
    }

    @GetMapping("/my-camps")
    @PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'BLOOD_BANK_REQUESTER')")
    public ResponseEntity<List<DonationCampDto>> getMyCamps(Authentication auth) {
        return ResponseEntity.ok(campService.getMyCamps(auth.getName()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'BLOOD_BANK_REQUESTER')")
    public ResponseEntity<DonationCampDto> createCamp(@Valid @RequestBody CreateCampDto dto, Authentication auth) {
        return ResponseEntity.ok(campService.createCamp(dto, auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'BLOOD_BANK_REQUESTER')")
    public ResponseEntity<DonationCampDto> updateCamp(
            @PathVariable Long id, 
            @Valid @RequestBody CreateCampDto dto, 
            Authentication auth) {
        return ResponseEntity.ok(campService.updateCamp(id, dto, auth.getName()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'BLOOD_BANK_REQUESTER')")
    public ResponseEntity<DonationCampDto> updateCampStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, String> body, 
            Authentication auth) {
        String status = body.get("status");
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(campService.updateCampStatus(id, status, auth.getName()));
    }

    @PostMapping("/{id}/register")
    @PreAuthorize("hasRole('DONOR')")
    @Deprecated
    public ResponseEntity<?> registerForCamp(@PathVariable Long id, Authentication auth) {
        // Superseded by the slot booking system. Slot booking is now the only way to register
        // for a camp. Use GET /api/camps/{id}/slots to view available windows and
        // POST /api/slots/{slotId}/book to reserve a specific time slot.
        return ResponseEntity.status(org.springframework.http.HttpStatus.GONE)
                .body(Map.of("message",
                        "Camp pre-registration has been replaced by slot booking. "
                        + "Use GET /api/camps/" + id + "/slots to view available time windows "
                        + "and POST /api/slots/{slotId}/book to reserve your spot."));
    }
}
