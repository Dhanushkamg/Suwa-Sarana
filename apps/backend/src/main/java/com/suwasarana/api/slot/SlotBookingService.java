package com.suwasarana.api.slot;

import com.suwasarana.api.camp.DonationCamp;
import com.suwasarana.api.camp.DonationCampRepository;
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
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SlotBookingService {

    private static final Logger log = LoggerFactory.getLogger(SlotBookingService.class);

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
    public DonationSlotDto createCampSlot(Long campId, CreateSlotDto dto) {
        DonationCamp camp = campRepository.findById(campId)
                .orElseThrow(() -> new RuntimeException("Camp not found: " + campId));
        DonationSlot slot = buildSlot(dto.getStartTime(), dto.getEndTime(), dto.getCapacity());
        slot.setCamp(camp);
        return mapSlotToDto(slotRepository.save(slot));
    }

    @Transactional
    public List<DonationSlotDto> bulkCreateCampSlots(Long campId, BulkCreateSlotDto dto) {
        DonationCamp camp = campRepository.findById(campId)
                .orElseThrow(() -> new RuntimeException("Camp not found: " + campId));
        List<DonationSlot> slots = generateSlots(dto);
        slots.forEach(s -> s.setCamp(camp));
        return slotRepository.saveAll(slots).stream().map(this::mapSlotToDto).collect(Collectors.toList());
    }

    @Transactional
    public DonationSlotDto createHospitalSlot(Long hospitalUserId, CreateSlotDto dto) {
        User hospitalUser = userRepository.findById(hospitalUserId)
                .orElseThrow(() -> new RuntimeException("Hospital user not found: " + hospitalUserId));
        DonationSlot slot = buildSlot(dto.getStartTime(), dto.getEndTime(), dto.getCapacity());
        slot.setHospitalUser(hospitalUser);
        return mapSlotToDto(slotRepository.save(slot));
    }

    @Transactional
    public List<DonationSlotDto> bulkCreateHospitalSlots(Long hospitalUserId, BulkCreateSlotDto dto) {
        User hospitalUser = userRepository.findById(hospitalUserId)
                .orElseThrow(() -> new RuntimeException("Hospital user not found: " + hospitalUserId));
        List<DonationSlot> slots = generateSlots(dto);
        slots.forEach(s -> s.setHospitalUser(hospitalUser));
        return slotRepository.saveAll(slots).stream().map(this::mapSlotToDto).collect(Collectors.toList());
    }

    private DonationSlot buildSlot(LocalDateTime start, LocalDateTime end, int capacity) {
        DonationSlot slot = new DonationSlot();
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setCapacity(capacity);
        return slot;
    }

    private List<DonationSlot> generateSlots(BulkCreateSlotDto dto) {
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

        // 4. Duplicate booking guard (before the DB unique constraint fires)
        if (bookingRepository.findBySlotIdAndDonorId(slotId, donor.getId()).isPresent()) {
            throw new DuplicateBookingException();
        }

        // 5. NBTS eligibility — correct call site: takes (DonorProfile, LocalDate), returns EligibilityResult
        EligibilityResult eligibility = deferralService.checkEligibility(donor, LocalDate.now());
        if (!eligibility.isEligible()) {
            throw new DonorIneligibleException(eligibility.getReasons());
        }

        // 6. Create booking record
        SlotBooking booking = new SlotBooking();
        booking.setSlot(slot);
        booking.setDonor(donor);
        booking.setStatus(BookingStatus.BOOKED);
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
                .orElseThrow(() -> new RuntimeException("Donor profile not found for user: " + userId));

        SlotBooking booking = bookingRepository.findBySlotIdAndDonorId(slotId, donor.getId())
                .orElseThrow(() -> new RuntimeException("Booking not found for slot " + slotId + " and donor " + donor.getId()));

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // Release capacity back to the slot
        DonationSlot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));
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
    public SlotBookingDto checkIn(Long slotId, Long bookingId) {
        SlotBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (!booking.getSlot().getId().equals(slotId)) {
            throw new RuntimeException("Booking does not belong to slot " + slotId);
        }
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new RuntimeException("Cannot check in a booking with status: " + booking.getStatus());
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
