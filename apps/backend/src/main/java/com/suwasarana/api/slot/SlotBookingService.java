package com.suwasarana.api.slot;

import com.suwasarana.api.camp.DonationCamp;
import com.suwasarana.api.camp.DonationCampRepository;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.donor.DeferralService;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.donor.EligibilityResult;
import com.suwasarana.api.exception.DonorIneligibleException;
import com.suwasarana.api.exception.DuplicateBookingException;
import com.suwasarana.api.exception.SlotFullException;
import com.suwasarana.api.exception.SlotNotFoundException;
import com.suwasarana.api.notification.NotificationMessage;
import com.suwasarana.api.notification.NotificationService;
import com.suwasarana.api.slot.dto.BulkCreateSlotDto;
import com.suwasarana.api.slot.dto.CreateSlotDto;
import com.suwasarana.api.slot.dto.DonationSlotDto;
import com.suwasarana.api.slot.dto.SlotBookingDto;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SlotBookingService {

    private static final Logger log = LoggerFactory.getLogger(SlotBookingService.class);
    private static final int MAX_BULK_SLOTS = 200;

    @Autowired private DonationSlotRepository slotRepository;
    @Autowired private SlotBookingRepository bookingRepository;
    @Autowired private DonorRepository donorRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DonationCampRepository campRepository;
    @Autowired private DeferralService deferralService;
    @Autowired private NotificationService notificationService;

    // -------------------------------------------------------------------------
    // Slot Creation
    // -------------------------------------------------------------------------

    @Transactional
    public DonationSlotDto createCampSlot(Long campId, CreateSlotDto dto, Long callerId) {
        DonationCamp camp = loadManagedCamp(campId, callerId);
        DonationSlot slot = buildSlot(dto.getStartTime(), dto.getEndTime(), dto.getCapacity());
        slot.setCamp(camp);
        return mapSlotToDto(slotRepository.save(slot));
    }

    @Transactional
    public List<DonationSlotDto> bulkCreateCampSlots(Long campId, BulkCreateSlotDto dto, Long callerId) {
        DonationCamp camp = loadManagedCamp(campId, callerId);
        List<DonationSlot> slots = generateSlots(dto);
        slots.forEach(s -> s.setCamp(camp));
        return slotRepository.saveAll(slots).stream().map(this::mapSlotToDto).collect(Collectors.toList());
    }

    @Transactional
    public DonationSlotDto createHospitalSlot(Long hospitalUserId, CreateSlotDto dto, Long callerId) {
        User hospitalUser = loadManagedHospital(hospitalUserId, callerId);
        DonationSlot slot = buildSlot(dto.getStartTime(), dto.getEndTime(), dto.getCapacity());
        slot.setHospitalUser(hospitalUser);
        return mapSlotToDto(slotRepository.save(slot));
    }

    @Transactional
    public List<DonationSlotDto> bulkCreateHospitalSlots(Long hospitalUserId, BulkCreateSlotDto dto, Long callerId) {
        User hospitalUser = loadManagedHospital(hospitalUserId, callerId);
        List<DonationSlot> slots = generateSlots(dto);
        slots.forEach(s -> s.setHospitalUser(hospitalUser));
        return slotRepository.saveAll(slots).stream().map(this::mapSlotToDto).collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Ownership checks (ADMIN may manage anything)
    // -------------------------------------------------------------------------

    private User loadCaller(Long callerId) {
        return userRepository.findById(callerId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
    }

    /** Camp must exist and the caller must be its organizer (or ADMIN). */
    private DonationCamp loadManagedCamp(Long campId, Long callerId) {
        User caller = loadCaller(callerId);
        DonationCamp camp = campRepository.findById(campId)
                .orElseThrow(() -> ApiException.notFound("Camp not found: " + campId));
        if (caller.getRole() != Role.ADMIN && !camp.getOrganizer().getId().equals(caller.getId())) {
            throw ApiException.forbidden("You can only manage slots for your own camps");
        }
        return camp;
    }

    /** Target must be a hospital / blood bank user and the caller must be that user (or ADMIN). */
    private User loadManagedHospital(Long hospitalUserId, Long callerId) {
        User caller = loadCaller(callerId);
        if (caller.getRole() != Role.ADMIN && !caller.getId().equals(hospitalUserId)) {
            throw ApiException.forbidden("You can only manage your own slots");
        }
        User hospitalUser = userRepository.findById(hospitalUserId)
                .orElseThrow(() -> ApiException.notFound("Hospital user not found: " + hospitalUserId));
        if (hospitalUser.getRole() != Role.HOSPITAL_REQUESTER && hospitalUser.getRole() != Role.BLOOD_BANK_REQUESTER) {
            throw ApiException.badRequest("User is not a hospital or blood bank");
        }
        return hospitalUser;
    }

    /** The slot's owner (camp organizer or hospital user) must be the caller, unless ADMIN. */
    private void assertCanManageSlot(DonationSlot slot, User caller) {
        if (caller.getRole() == Role.ADMIN) return;
        Long ownerId = slot.getCamp() != null
                ? slot.getCamp().getOrganizer().getId()
                : (slot.getHospitalUser() != null ? slot.getHospitalUser().getId() : null);
        if (ownerId == null || !ownerId.equals(caller.getId())) {
            throw ApiException.forbidden("You can only manage bookings for your own slots");
        }
    }

    private DonationSlot buildSlot(LocalDateTime start, LocalDateTime end, int capacity) {
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("End time must be after start time");
        }
        DonationSlot slot = new DonationSlot();
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setCapacity(capacity);
        return slot;
    }

    private List<DonationSlot> generateSlots(BulkCreateSlotDto dto) {
        if (!dto.getOverallEndTime().isAfter(dto.getOverallStartTime())) {
            throw ApiException.badRequest("Overall end time must be after overall start time");
        }
        if (dto.getIntervalMinutes() <= 0) {
            throw ApiException.badRequest("Interval must be greater than 0 minutes");
        }
        long count = Duration.between(dto.getOverallStartTime(), dto.getOverallEndTime()).toMinutes()
                / dto.getIntervalMinutes();
        if (count < 1) {
            throw ApiException.badRequest("Interval is longer than the overall time window");
        }
        if (count > MAX_BULK_SLOTS) {
            throw ApiException.badRequest("Cannot generate more than " + MAX_BULK_SLOTS + " slots at once");
        }
        List<DonationSlot> slots = new ArrayList<>();
        LocalDateTime cursor = dto.getOverallStartTime();
        while (cursor.plusMinutes(dto.getIntervalMinutes()).compareTo(dto.getOverallEndTime()) <= 0) {
            LocalDateTime slotEnd = cursor.plusMinutes(dto.getIntervalMinutes());
            slots.add(buildSlot(cursor, slotEnd, dto.getCapacityPerSlot()));
            cursor = slotEnd;
        }
        return slots;
    }

    // -------------------------------------------------------------------------
    // Slot Listing
    // -------------------------------------------------------------------------

    public List<DonationSlotDto> getSlotsForCamp(Long campId, boolean openOnly) {
        List<DonationSlot> slots = openOnly
                ? slotRepository.findByCampIdAndStatusOrderByStartTimeAsc(campId, SlotStatus.OPEN)
                : slotRepository.findByCampIdOrderByStartTimeAsc(campId);
        return slots.stream().map(this::mapSlotToDto).collect(Collectors.toList());
    }

    public List<DonationSlotDto> getSlotsForHospital(Long hospitalUserId, boolean openOnly) {
        List<DonationSlot> slots = openOnly
                ? slotRepository.findByHospitalUserIdAndStatusOrderByStartTimeAsc(hospitalUserId, SlotStatus.OPEN)
                : slotRepository.findByHospitalUserIdOrderByStartTimeAsc(hospitalUserId);
        return slots.stream().map(this::mapSlotToDto).collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Booking
    // -------------------------------------------------------------------------

    /**
     * Books a slot for the authenticated donor.
     * Uses a pessimistic write lock on the slot row to prevent concurrent over-booking.
     * Calls DeferralService with the correct signature (DonorProfile + LocalDate).
     */
    @Transactional
    public SlotBookingDto bookSlot(Long slotId, Long userId) {
        // 1. Resolve donor profile from the authenticated user's ID
        DonorProfile donor = donorRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Donor profile not found for user: " + userId));

        // 2. Acquire pessimistic write lock — prevents race condition on bookedCount
        DonationSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        // 3. Capacity / status guard
        if (slot.getStatus() != SlotStatus.OPEN || slot.getBookedCount() >= slot.getCapacity()) {
            throw new SlotFullException();
        }

        // 4. Duplicate booking guard (before the DB unique constraint fires).
        //    A CANCELLED booking is not a duplicate: its row is reused below, because of UNIQUE(slot_id, donor_id).
        SlotBooking existing = bookingRepository.findBySlotIdAndDonorId(slotId, donor.getId()).orElse(null);
        if (existing != null && existing.getStatus() != BookingStatus.CANCELLED) {
            throw new DuplicateBookingException();
        }

        // 5. NBTS eligibility — correct call site: takes (DonorProfile, LocalDate), returns EligibilityResult
        EligibilityResult eligibility = deferralService.checkEligibility(donor, LocalDate.now());
        if (!eligibility.isEligible()) {
            throw new DonorIneligibleException(eligibility.getReasons());
        }

        // 6. Create the booking record, or revive the donor's cancelled one
        SlotBooking booking = existing != null ? existing : new SlotBooking();
        booking.setSlot(slot);
        booking.setDonor(donor);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedAt(Instant.now());
        booking.setCheckedInAt(null);
        booking.setEligibleAsOf(LocalDate.now());
        SlotBooking saved = bookingRepository.save(booking);

        // 7. Update slot counters transactionally
        slot.setBookedCount(slot.getBookedCount() + 1);
        if (slot.getBookedCount() >= slot.getCapacity()) {
            slot.setStatus(SlotStatus.FULL);
        }
        slotRepository.save(slot);

        // 8. Send booking confirmation via existing SSE → SMS cascade (generic overload)
        NotificationMessage msg = new NotificationMessage(
                "Slot Booked!",
                "Your donation slot at " + slot.resolveHostName() + " is confirmed for "
                        + slot.getStartTime() + ". See My Bookings for your QR code.",
                "SLOT_CONFIRMED",
                saved.getId()
        );
        try {
            notificationService.notify(donor, msg);
        } catch (Exception e) {
            // Notification failure must never roll back the booking
            log.warn("Failed to send slot confirmation notification for booking {}: {}", saved.getId(), e.getMessage());
        }

        log.info("Donor {} booked slot {} (booking {})", donor.getId(), slotId, saved.getId());
        return mapBookingToDto(saved);
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    @Transactional
    public SlotBookingDto cancelBooking(Long slotId, Long userId) {
        DonorProfile donor = donorRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Donor profile not found for user: " + userId));

        // Lock the slot first so two concurrent cancels cannot both release capacity
        DonationSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        SlotBooking booking = bookingRepository.findBySlotIdAndDonorId(slotId, donor.getId())
                .orElseThrow(() -> ApiException.notFound("Booking not found for slot " + slotId));

        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw ApiException.conflict("Only a booked slot can be cancelled (current status: " + booking.getStatus() + ")");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // Release capacity back to the slot
        if (slot.getBookedCount() > 0) {
            slot.setBookedCount(slot.getBookedCount() - 1);
        }
        if (slot.getStatus() == SlotStatus.FULL) {
            slot.setStatus(SlotStatus.OPEN);
        }
        slotRepository.save(slot);

        log.info("Donor {} cancelled booking for slot {}", donor.getId(), slotId);
        return mapBookingToDto(booking);
    }

    // -------------------------------------------------------------------------
    // Check-In
    // -------------------------------------------------------------------------

    @Transactional
    public SlotBookingDto checkIn(Long slotId, Long bookingId, Long callerId) {
        User caller = loadCaller(callerId);
        SlotBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found: " + bookingId));

        if (!booking.getSlot().getId().equals(slotId)) {
            throw ApiException.badRequest("Booking does not belong to slot " + slotId);
        }
        assertCanManageSlot(booking.getSlot(), caller);
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw ApiException.conflict("Cannot check in a booking with status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckedInAt(java.time.Instant.now());
        bookingRepository.save(booking);

        log.info("Checked in booking {} for slot {}", bookingId, slotId);
        return mapBookingToDto(booking);
    }

    // -------------------------------------------------------------------------
    // My Bookings (donor view)
    // -------------------------------------------------------------------------

    public List<SlotBookingDto> getMyBookings(Long userId) {
        DonorProfile donor = donorRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Donor profile not found for user: " + userId));
        return bookingRepository.findByDonorIdOrderByBookedAtDesc(donor.getId())
                .stream()
                .map(this::mapBookingToDto)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    public DonationSlotDto mapSlotToDto(DonationSlot slot) {
        DonationSlotDto dto = new DonationSlotDto();
        dto.setId(slot.getId());
        dto.setHostName(slot.resolveHostName());
        dto.setStartTime(slot.getStartTime());
        dto.setEndTime(slot.getEndTime());
        dto.setCapacity(slot.getCapacity());
        dto.setBookedCount(slot.getBookedCount());
        dto.setSpotsLeft(slot.getCapacity() - slot.getBookedCount());
        dto.setStatus(slot.getStatus());
        return dto;
    }

    private SlotBookingDto mapBookingToDto(SlotBooking booking) {
        SlotBookingDto dto = new SlotBookingDto();
        dto.setId(booking.getId());
        dto.setSlotId(booking.getSlot().getId());
        dto.setDonorId(booking.getDonor().getId());
        dto.setStatus(booking.getStatus());
        dto.setBookedAt(booking.getBookedAt());
        dto.setCheckedInAt(booking.getCheckedInAt());
        dto.setSlot(mapSlotToDto(booking.getSlot()));
        return dto;
    }
}
