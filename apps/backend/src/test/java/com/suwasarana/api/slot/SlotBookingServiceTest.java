package com.suwasarana.api.slot;

import com.suwasarana.api.camp.DonationCamp;
import com.suwasarana.api.camp.DonationCampRepository;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.donor.DeferralService;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.donor.EligibilityResult;
import com.suwasarana.api.exception.DuplicateBookingException;
import com.suwasarana.api.notification.NotificationService;
import com.suwasarana.api.slot.dto.BulkCreateSlotDto;
import com.suwasarana.api.slot.dto.CreateSlotDto;
import com.suwasarana.api.slot.dto.SlotBookingDto;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SlotBookingServiceTest {

    @Mock private DonationSlotRepository slotRepository;
    @Mock private SlotBookingRepository bookingRepository;
    @Mock private DonorRepository donorRepository;
    @Mock private UserRepository userRepository;
    @Mock private DonationCampRepository campRepository;
    @Mock private DeferralService deferralService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private SlotBookingService service;

    private User organizer;
    private DonationCamp camp;
    private DonationSlot slot;
    private DonorProfile donor;

    @BeforeEach
    void setUp() {
        organizer = user(10L, Role.HOSPITAL_REQUESTER);

        camp = new DonationCamp();
        camp.setId(1L);
        camp.setName("Drive");
        camp.setOrganizer(organizer);

        slot = new DonationSlot();
        slot.setId(100L);
        slot.setCamp(camp);
        slot.setCapacity(2);
        slot.setBookedCount(1);
        slot.setStatus(SlotStatus.OPEN);
        slot.setStartTime(LocalDateTime.now().plusDays(1));
        slot.setEndTime(LocalDateTime.now().plusDays(1).plusMinutes(30));

        donor = new DonorProfile();
        donor.setId(20L);
    }

    private User user(Long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private SlotBooking booking(BookingStatus status) {
        SlotBooking b = new SlotBooking();
        b.setId(500L);
        b.setSlot(slot);
        b.setDonor(donor);
        b.setStatus(status);
        return b;
    }

    // ---- cancel ---------------------------------------------------------

    @Test
    void cancelBooking_releasesCapacityOnce() {
        SlotBooking b = booking(BookingStatus.BOOKED);
        when(donorRepository.findByUserId(5L)).thenReturn(Optional.of(donor));
        when(slotRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findBySlotIdAndDonorId(100L, 20L)).thenReturn(Optional.of(b));

        SlotBookingDto dto = service.cancelBooking(100L, 5L);

        assertEquals(BookingStatus.CANCELLED, dto.getStatus());
        assertEquals(0, slot.getBookedCount());
    }

    @Test
    void cancelBooking_alreadyCancelledIsConflictAndDoesNotChangeCount() {
        SlotBooking b = booking(BookingStatus.CANCELLED);
        when(donorRepository.findByUserId(5L)).thenReturn(Optional.of(donor));
        when(slotRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findBySlotIdAndDonorId(100L, 20L)).thenReturn(Optional.of(b));

        ApiException ex = assertThrows(ApiException.class, () -> service.cancelBooking(100L, 5L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(1, slot.getBookedCount());
    }

    @Test
    void cancelBooking_checkedInIsConflict() {
        SlotBooking b = booking(BookingStatus.CHECKED_IN);
        when(donorRepository.findByUserId(5L)).thenReturn(Optional.of(donor));
        when(slotRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findBySlotIdAndDonorId(100L, 20L)).thenReturn(Optional.of(b));

        ApiException ex = assertThrows(ApiException.class, () -> service.cancelBooking(100L, 5L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // ---- rebook ---------------------------------------------------------

    @Test
    void bookSlot_reusesCancelledBookingRow() {
        SlotBooking cancelled = booking(BookingStatus.CANCELLED);
        cancelled.setCheckedInAt(java.time.Instant.now());
        when(donorRepository.findByUserId(5L)).thenReturn(Optional.of(donor));
        when(slotRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findBySlotIdAndDonorId(100L, 20L)).thenReturn(Optional.of(cancelled));
        when(deferralService.checkEligibility(any(DonorProfile.class), any(LocalDate.class)))
                .thenReturn(EligibilityResult.eligible());
        when(bookingRepository.save(any(SlotBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        service.bookSlot(100L, 5L);

        assertEquals(BookingStatus.BOOKED, cancelled.getStatus());
        assertNull(cancelled.getCheckedInAt());
        assertEquals(2, slot.getBookedCount());
        assertEquals(SlotStatus.FULL, slot.getStatus());
        verify(bookingRepository).save(cancelled); // same row, no new insert
    }

    @Test
    void bookSlot_activeBookingIsDuplicate() {
        when(donorRepository.findByUserId(5L)).thenReturn(Optional.of(donor));
        when(slotRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findBySlotIdAndDonorId(100L, 20L))
                .thenReturn(Optional.of(booking(BookingStatus.BOOKED)));

        assertThrows(DuplicateBookingException.class, () -> service.bookSlot(100L, 5L));
    }

    // ---- ownership ------------------------------------------------------

    @Test
    void createCampSlot_otherRequesterIsForbidden() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L, Role.HOSPITAL_REQUESTER)));
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.createCampSlot(1L, createDto(), 11L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(slotRepository, never()).save(any());
    }

    @Test
    void createCampSlot_organizerAllowed() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(organizer));
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));
        when(slotRepository.save(any(DonationSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNotNull(service.createCampSlot(1L, createDto(), 10L));
    }

    @Test
    void createCampSlot_adminAllowed() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L, Role.ADMIN)));
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));
        when(slotRepository.save(any(DonationSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNotNull(service.createCampSlot(1L, createDto(), 99L));
    }

    @Test
    void createHospitalSlot_forAnotherUserIsForbidden() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L, Role.HOSPITAL_REQUESTER)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.createHospitalSlot(10L, createDto(), 11L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void createHospitalSlot_targetMustBeHospitalOrBloodBank() {
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L, Role.ADMIN)));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.DONOR)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.createHospitalSlot(5L, createDto(), 99L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void checkIn_otherRequesterIsForbidden() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L, Role.HOSPITAL_REQUESTER)));
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking(BookingStatus.BOOKED)));

        ApiException ex = assertThrows(ApiException.class, () -> service.checkIn(100L, 500L, 11L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void checkIn_organizerSucceeds() {
        SlotBooking b = booking(BookingStatus.BOOKED);
        when(userRepository.findById(10L)).thenReturn(Optional.of(organizer));
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(b));

        SlotBookingDto dto = service.checkIn(100L, 500L, 10L);

        assertEquals(BookingStatus.CHECKED_IN, dto.getStatus());
    }

    // ---- validation -----------------------------------------------------

    @Test
    void createCampSlot_endBeforeStartIsBadRequest() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(organizer));
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));
        CreateSlotDto dto = createDto();
        dto.setEndTime(dto.getStartTime().minusMinutes(5));

        ApiException ex = assertThrows(ApiException.class, () -> service.createCampSlot(1L, dto, 10L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void bulkCreate_tooManySlotsIsBadRequest() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(organizer));
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));
        BulkCreateSlotDto dto = new BulkCreateSlotDto();
        dto.setOverallStartTime(LocalDateTime.now().plusDays(1));
        dto.setOverallEndTime(LocalDateTime.now().plusDays(30));
        dto.setIntervalMinutes(5);
        dto.setCapacityPerSlot(1);

        ApiException ex = assertThrows(ApiException.class, () -> service.bulkCreateCampSlots(1L, dto, 10L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void bulkCreate_endBeforeStartIsBadRequest() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(organizer));
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));
        BulkCreateSlotDto dto = new BulkCreateSlotDto();
        dto.setOverallStartTime(LocalDateTime.now().plusDays(2));
        dto.setOverallEndTime(LocalDateTime.now().plusDays(1));
        dto.setIntervalMinutes(30);
        dto.setCapacityPerSlot(1);

        ApiException ex = assertThrows(ApiException.class, () -> service.bulkCreateCampSlots(1L, dto, 10L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    private CreateSlotDto createDto() {
        CreateSlotDto dto = new CreateSlotDto();
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(1).plusMinutes(30));
        dto.setCapacity(3);
        return dto;
    }
}
