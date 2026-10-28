package com.suwasarana.api.slot;

import com.suwasarana.api.camp.DonationCamp;
import com.suwasarana.api.camp.DonationCampRepository;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.donor.DeferralService;
import com.suwasarana.api.donor.DonationRecorder;
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
import com.suwasarana.api.slot.dto.SlotRosterEntryDto;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.shared.CodedApiException;
import com.suwasarana.api.shared.ErrorCodes;
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
import java.util.LinkedHashMap;
import java.util.Map;
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
    @Autowired private DonationRecorder donationRecorder;
    @Autowired private AppClock clock;

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
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.CAMP_NOT_FOUND, "Camp not found"));
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
            throw CodedApiException.badRequest(ErrorCodes.TIME_RANGE_INVALID, "End time must be after start time");
        }
        if (!start.isAfter(clock.now())) {
            throw CodedApiException.badRequest(ErrorCodes.SLOT_IN_PAST, "A slot must start in the future");
        }
        DonationSlot slot = new DonationSlot();
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setCapacity(capacity);
        return slot;
    }

    private List<DonationSlot> generateSlots(BulkCreateSlotDto dto) {
        if (!dto.getOverallEndTime().isAfter(dto.getOverallStartTime())) {
            throw CodedApiException.badRequest(ErrorCodes.TIME_RANGE_INVALID, "Overall end time must be after overall start time");
        }
        if (!dto.getOverallStartTime().isAfter(clock.now())) {
            throw CodedApiException.badRequest(ErrorCodes.SLOT_IN_PAST, "Slots must start in the future");
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

    @Transactional(readOnly = true)
    public List<DonationSlotDto> getSlotsForCamp(Long campId, boolean openOnly) {
        List<DonationSlot> slots = openOnly
                ? slotRepository.findByCampIdAndStatusOrderByStartTimeAsc(campId, SlotStatus.OPEN)
                : slotRepository.findByCampIdOrderByStartTimeAsc(campId);
        return slots.stream().map(this::mapSlotToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
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
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.DONOR_PROFILE_NOT_FOUND, "Donor profile not found"));

        // 2. Acquire pessimistic write lock — prevents race condition on bookedCount
        DonationSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        // 3. Only future slots can be booked
        if (!slot.getStartTime().isAfter(clock.now())) {
            throw CodedApiException.conflict(ErrorCodes.SLOT_IN_PAST, "This slot has already started or ended");
        }

        // 4. Capacity / status guard
        if (slot.getStatus() != SlotStatus.OPEN || slot.getBookedCount() >= slot.getCapacity()) {
            throw new SlotFullException();
        }

        // 5. Duplicate booking guard (before the DB unique constraint fires).
        //    A CANCELLED booking is not a duplicate: its row is reused below, because of UNIQUE(slot_id, donor_id).
        SlotBooking existing = bookingRepository.findBySlotIdAndDonorId(slotId, donor.getId()).orElse(null);
        if (existing != null && existing.getStatus() != BookingStatus.CANCELLED) {
            throw new DuplicateBookingException();
        }

        // 6. A donor cannot be in two places at once
        if (bookingRepository.countOverlappingBookings(donor.getId(), slot.getStartTime(), slot.getEndTime(), slotId) > 0) {
            throw CodedApiException.conflict(ErrorCodes.BOOKING_OVERLAP, "You already have a booking that overlaps this time");
        }

        // 7. NBTS eligibility on the DAY OF THE SLOT (not today): a donor deferred until next week can book next month
        LocalDate slotDate = slot.getStartTime().toLocalDate();
        EligibilityResult eligibility = deferralService.checkEligibility(donor, slotDate);
        if (!eligibility.isEligible()) {
            throw new DonorIneligibleException(eligibility.getReasons());
        }

        // 8. Create the booking record, or revive the donor's cancelled one
        SlotBooking booking = existing != null ? existing : new SlotBooking();
        booking.setSlot(slot);
        booking.setDonor(donor);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedAt(clock.instant());
        booking.setCheckedInAt(null);
        booking.setEligibleAsOf(slotDate);
        SlotBooking saved = bookingRepository.save(booking);

        // 9. Update slot counters transactionally
        slot.setBookedCount(slot.getBookedCount() + 1);
        if (slot.getBookedCount() >= slot.getCapacity()) {
            slot.setStatus(SlotStatus.FULL);
        }
        slotRepository.save(slot);

        // 10. Booking confirmation through the inbox -> SSE -> SMS cascade (generic overload)
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("host", slot.resolveHostName());
        params.put("start", slot.getStartTime().toString());
        params.put("bookingId", saved.getId());
        NotificationMessage msg = new NotificationMessage(
                "Slot Booked!",
                "Your donation slot at " + slot.resolveHostName() + " is confirmed for "
                        + slot.getStartTime() + ". See My Bookings for your QR code.",
                "SLOT_CONFIRMED",
                saved.getId()
        ).withKeys("notifications.slot.confirmed.title", "notifications.slot.confirmed.body", params);
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
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.DONOR_PROFILE_NOT_FOUND, "Donor profile not found"));

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

    /**
     * Staff mark the donor as arrived. This is the moment the donation is recorded: history row, donor totals,
     * the 4-month deferral and the reliability reward all happen in {@link DonationRecorder}, the same code path
     * as confirming a request match.
     */
    @Transactional
    public SlotBookingDto checkIn(Long slotId, Long bookingId, Long callerId) {
        User caller = loadCaller(callerId);
        SlotBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found"));

        if (!booking.getSlot().getId().equals(slotId)) {
            throw ApiException.badRequest("Booking does not belong to slot " + slotId);
        }
        assertCanManageSlot(booking.getSlot(), caller);
        if (booking.getSlot().getStatus() == SlotStatus.CANCELLED) {
            throw ApiException.conflict("This slot was cancelled");
        }
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw ApiException.conflict("Cannot check in a booking with status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckedInAt(clock.instant());
        bookingRepository.save(booking);
        donationRecorder.recordForBooking(booking);

        log.info("Checked in booking {} for slot {}", bookingId, slotId);
        return mapBookingToDto(booking);
    }

    // -------------------------------------------------------------------------
    // Organiser view of a slot's bookings
    // -------------------------------------------------------------------------

    /**
     * The people booked into one slot, for the camp organiser / hospital / admin who owns it.
     * Shows first name, last initial, blood type and booking status; no phone number or e-mail.
     */
    @Transactional(readOnly = true)
    public List<SlotRosterEntryDto> getSlotBookings(Long slotId, Long callerId) {
        User caller = loadCaller(callerId);
        DonationSlot slot = slotRepository.findById(slotId).orElseThrow(() -> new SlotNotFoundException(slotId));
        assertCanManageSlot(slot, caller);
        return bookingRepository.findRosterBySlotId(slotId).stream()
                .map(SlotRosterEntryDto::from)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // My Bookings (donor view)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SlotBookingDto> getMyBookings(Long userId) {
        DonorProfile donor = donorRepository.findByUserId(userId)
                .orElseThrow(() -> CodedApiException.notFound(ErrorCodes.DONOR_PROFILE_NOT_FOUND, "Donor profile not found"));
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
