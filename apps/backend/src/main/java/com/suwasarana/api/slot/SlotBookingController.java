package com.suwasarana.api.slot;

import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.security.UserDetailsImpl;
import com.suwasarana.api.slot.dto.BulkCreateSlotDto;
import com.suwasarana.api.slot.dto.CreateSlotDto;
import com.suwasarana.api.slot.dto.DonationSlotDto;
import com.suwasarana.api.slot.dto.SlotBookingDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Donation Slots", description = "Time-slotted booking for camp and hospital donation windows")
public class SlotBookingController {

    @Autowired
    private SlotBookingService slotBookingService;

    // -------------------------------------------------------------------------
    // Camp Slots
    // -------------------------------------------------------------------------

    @Operation(summary = "List open slots for a camp")
    @GetMapping("/api/camps/{campId}/slots")
    public ResponseEntity<ApiResponse<List<DonationSlotDto>>> getCampSlots(
            @PathVariable Long campId,
            @RequestParam(defaultValue = "true") boolean openOnly) {
        return ResponseEntity.ok(ApiResponse.success(slotBookingService.getSlotsForCamp(campId, openOnly)));
    }

    @Operation(summary = "Create a single slot for a camp")
    @PostMapping("/api/camps/{campId}/slots")
    @PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'ADMIN')")
    public ResponseEntity<ApiResponse<DonationSlotDto>> createCampSlot(
            @PathVariable Long campId,
            @Valid @RequestBody CreateSlotDto dto) {
        return ResponseEntity.ok(ApiResponse.success(slotBookingService.createCampSlot(campId, dto)));
    }

    @Operation(summary = "Bulk-generate slots for a camp at regular intervals")
    @PostMapping("/api/camps/{campId}/slots/bulk")
    @PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<DonationSlotDto>>> bulkCreateCampSlots(
            @PathVariable Long campId,
            @Valid @RequestBody BulkCreateSlotDto dto) {
        return ResponseEntity.ok(ApiResponse.success(slotBookingService.bulkCreateCampSlots(campId, dto)));
    }

    // -------------------------------------------------------------------------
    // Hospital Slots
    // -------------------------------------------------------------------------

    @Operation(summary = "List open slots for a hospital walk-in window")
    @GetMapping("/api/hospitals/{hospitalUserId}/slots")
    public ResponseEntity<ApiResponse<List<DonationSlotDto>>> getHospitalSlots(
            @PathVariable Long hospitalUserId,
            @RequestParam(defaultValue = "true") boolean openOnly) {
        return ResponseEntity.ok(ApiResponse.success(slotBookingService.getSlotsForHospital(hospitalUserId, openOnly)));
    }

    @Operation(summary = "Create a single slot for a hospital walk-in window")
    @PostMapping("/api/hospitals/{hospitalUserId}/slots")
    @PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'ADMIN')")
    public ResponseEntity<ApiResponse<DonationSlotDto>> createHospitalSlot(
            @PathVariable Long hospitalUserId,
            @Valid @RequestBody CreateSlotDto dto) {
        return ResponseEntity.ok(ApiResponse.success(slotBookingService.createHospitalSlot(hospitalUserId, dto)));
    }

    @Operation(summary = "Bulk-generate slots for a hospital walk-in window")
    @PostMapping("/api/hospitals/{hospitalUserId}/slots/bulk")
    @PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<DonationSlotDto>>> bulkCreateHospitalSlots(
            @PathVariable Long hospitalUserId,
            @Valid @RequestBody BulkCreateSlotDto dto) {
        return ResponseEntity.ok(ApiResponse.success(slotBookingService.bulkCreateHospitalSlots(hospitalUserId, dto)));
    }

    // -------------------------------------------------------------------------
    // Booking Actions
    // -------------------------------------------------------------------------

    @Operation(summary = "Book a donation slot", description = "Donor books a specific time slot. Enforces NBTS eligibility and concurrency-safe capacity tracking.")
    @PostMapping("/api/slots/{slotId}/book")
    @PreAuthorize("hasRole('DONOR')")
    public ResponseEntity<ApiResponse<SlotBookingDto>> bookSlot(
            @PathVariable Long slotId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(ApiResponse.success(slotBookingService.bookSlot(slotId, userDetails.getId()), "Slot booked successfully"));
    }

    @Operation(summary = "Cancel own slot booking")
    @DeleteMapping("/api/slots/{slotId}/book")
    @PreAuthorize("hasRole('DONOR')")
    public ResponseEntity<ApiResponse<SlotBookingDto>> cancelBooking(
            @PathVariable Long slotId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(ApiResponse.success(slotBookingService.cancelBooking(slotId, userDetails.getId()), "Booking cancelled"));
    }

    @Operation(summary = "Mark a donor as checked in", description = "Used by hospital/organiser staff when a donor arrives. Updates booking status to CHECKED_IN.")
    @PostMapping("/api/slots/{slotId}/check-in/{bookingId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_REQUESTER', 'ADMIN')")
    public ResponseEntity<ApiResponse<SlotBookingDto>> checkIn(
            @PathVariable Long slotId,
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(ApiResponse.success(slotBookingService.checkIn(slotId, bookingId), "Donor checked in"));
    }

    // -------------------------------------------------------------------------
    // My Bookings
    // -------------------------------------------------------------------------

    @Operation(summary = "Get authenticated donor's bookings", description = "Returns upcoming and past bookings for the authenticated donor, sorted most-recent first.")
    @GetMapping("/api/donors/me/bookings")
    @PreAuthorize("hasRole('DONOR')")
    public ResponseEntity<ApiResponse<List<SlotBookingDto>>> getMyBookings(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(ApiResponse.success(slotBookingService.getMyBookings(userDetails.getId())));
    }
}
