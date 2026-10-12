package com.suwasarana.api.camp;

import com.suwasarana.api.camp.dto.CreateCampDto;
import com.suwasarana.api.camp.dto.DonationCampDto;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.slot.BookingStatus;
import com.suwasarana.api.slot.DonationSlot;
import com.suwasarana.api.slot.DonationSlotRepository;
import com.suwasarana.api.slot.SlotBooking;
import com.suwasarana.api.slot.SlotBookingRepository;
import com.suwasarana.api.slot.SlotStatus;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DonationCampServiceTest {

    @Mock
    private DonationCampRepository campRepository;

    @Mock
    private CampRegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DonorRepository donorRepository;

    @Mock
    private DonationSlotRepository slotRepository;

    @Mock
    private SlotBookingRepository bookingRepository;

    @InjectMocks
    private DonationCampService campService;

    private DonationCamp campFor(String email, String requiredGroups) {
        User organizer = new User();
        organizer.setId(10L);
        organizer.setEmail(email);
        organizer.setFirstName("Nimal");
        organizer.setLastName("Perera");

        DonationCamp camp = new DonationCamp();
        camp.setId(1L);
        camp.setName("Drive");
        camp.setOrganizer(organizer);
        camp.setRequiredBloodGroups(requiredGroups);
        return camp;
    }

    private void stubDonor(String bloodType) {
        User user = new User();
        user.setId(5L);
        DonorProfile donor = new DonorProfile();
        donor.setBloodType(bloodType);
        when(userRepository.findByEmail("donor@test.lk")).thenReturn(Optional.of(user));
        when(donorRepository.findByUserId(5L)).thenReturn(Optional.of(donor));
    }

    @Test
    public void testRecommended_ExactBloodGroupMatchOnly() {
        stubDonor("B+");
        DonationCamp onlyAbPos = campFor("o@test.lk", "AB+");
        DonationCamp bAndO = campFor("o@test.lk", "O+, b+");
        when(campRepository.findByStatusOrderByScheduledDateAsc("SCHEDULED"))
                .thenReturn(List.of(onlyAbPos, bAndO));

        List<DonationCampDto> result = campService.getRecommendedCamps("donor@test.lk");

        assertEquals(1, result.size());
    }

    @Test
    public void testRecommended_NullDonorBloodTypeDoesNotThrow() {
        stubDonor(null);
        DonationCamp open = campFor("o@test.lk", null);
        DonationCamp restricted = campFor("o@test.lk", "B+");
        when(campRepository.findByStatusOrderByScheduledDateAsc("SCHEDULED"))
                .thenReturn(List.of(open, restricted));

        List<DonationCampDto> result = campService.getRecommendedCamps("donor@test.lk");

        assertEquals(1, result.size()); // only the camp with no requirement
    }

    @Test
    public void testUpdateCampStatus_InvalidStatusIsBadRequest() {
        DonationCamp camp = campFor("o@test.lk", null);
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));

        ApiException ex = assertThrows(ApiException.class, () -> campService.updateCampStatus(1L, "BOGUS", "o@test.lk"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    public void testUpdateCampStatus_NotOrganizerIsForbidden() {
        DonationCamp camp = campFor("o@test.lk", null);
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));

        ApiException ex = assertThrows(ApiException.class, () -> campService.updateCampStatus(1L, "COMPLETED", "other@test.lk"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    public void testUpdateCampStatus_CampNotFound() {
        when(campRepository.findById(9L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> campService.updateCampStatus(9L, "COMPLETED", "o@test.lk"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    public void testCancelCamp_CancelsSlotsAndBookingsAndKeepsCountConsistent() {
        DonationCamp camp = campFor("o@test.lk", null);
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));
        when(campRepository.save(any(DonationCamp.class))).thenAnswer(inv -> inv.getArgument(0));

        DonationSlot slot = new DonationSlot();
        slot.setId(100L);
        slot.setCapacity(2);
        slot.setBookedCount(2);
        slot.setStatus(SlotStatus.FULL);
        SlotBooking booked = new SlotBooking();
        booked.setStatus(BookingStatus.BOOKED);
        SlotBooking checkedIn = new SlotBooking();
        checkedIn.setStatus(BookingStatus.CHECKED_IN);

        when(slotRepository.findByCampIdOrderByStartTimeAsc(1L)).thenReturn(List.of(slot));
        when(slotRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findBySlotIdAndStatus(100L, BookingStatus.BOOKED)).thenReturn(List.of(booked));

        DonationCampDto result = campService.updateCampStatus(1L, "cancelled", "o@test.lk");

        assertEquals("CANCELLED", result.getStatus());
        assertEquals(SlotStatus.CANCELLED, slot.getStatus());
        assertEquals(BookingStatus.CANCELLED, booked.getStatus());
        assertEquals(BookingStatus.CHECKED_IN, checkedIn.getStatus());
        assertEquals(1, slot.getBookedCount()); // the checked-in donor still counts
    }

    @Test
    public void testCreateCamp_Success() {
        User organizer = new User();
        organizer.setId(10L);
        organizer.setEmail("organizer@hospital.lk");
        organizer.setFirstName("Nimal");
        organizer.setLastName("Perera");

        CreateCampDto dto = new CreateCampDto();
        dto.setName("Colombo Blood Drive");
        dto.setDistrict("Colombo");
        dto.setLocation("Town Hall");
        dto.setScheduledDate(LocalDate.now().plusDays(5));
        dto.setStartTime(LocalTime.of(9, 0));
        dto.setEndTime(LocalTime.of(15, 0));

        when(userRepository.findByEmail("organizer@hospital.lk")).thenReturn(Optional.of(organizer));
        when(campRepository.save(any(DonationCamp.class))).thenAnswer(inv -> {
            DonationCamp camp = inv.getArgument(0);
            camp.setId(1L);
            return camp;
        });

        DonationCampDto result = campService.createCamp(dto, "organizer@hospital.lk");

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Colombo Blood Drive", result.getName());
        assertEquals("Nimal Perera", result.getOrganizerName());
    }

    @Test
    public void testGetUpcomingCamps() {
        User organizer = new User();
        organizer.setEmail("org@test.lk");

        DonationCamp camp = new DonationCamp();
        camp.setId(1L);
        camp.setName("Kandy Drive");
        camp.setDistrict("Kandy");
        camp.setLocation("City Center");
        camp.setScheduledDate(LocalDate.now().plusDays(2));
        camp.setOrganizer(organizer);

        when(campRepository.findByStatusOrderByScheduledDateAsc("SCHEDULED"))
                .thenReturn(List.of(camp));

        List<DonationCampDto> camps = campService.getUpcomingCamps();
        assertEquals(1, camps.size());
        assertEquals("Kandy Drive", camps.get(0).getName());
    }

    @Test
    public void testRegisterForCamp_Success() {
        User user = new User();
        user.setId(5L);
        user.setEmail("donor@test.lk");

        DonorProfile donor = new DonorProfile();
        donor.setId(20L);
        donor.setUser(user);

        DonationCamp camp = new DonationCamp();
        camp.setId(1L);

        when(userRepository.findByEmail("donor@test.lk")).thenReturn(Optional.of(user));
        when(donorRepository.findByUserId(5L)).thenReturn(Optional.of(donor));
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));
        when(registrationRepository.existsByCampIdAndDonorId(1L, 20L)).thenReturn(false);

        campService.registerForCamp(1L, "donor@test.lk");

        verify(registrationRepository, times(1)).save(any(CampRegistration.class));
    }

    @Test
    public void testRegisterForCamp_AlreadyRegisteredThrows() {
        User user = new User();
        user.setId(5L);
        user.setEmail("donor@test.lk");

        DonorProfile donor = new DonorProfile();
        donor.setId(20L);

        DonationCamp camp = new DonationCamp();
        camp.setId(1L);

        when(userRepository.findByEmail("donor@test.lk")).thenReturn(Optional.of(user));
        when(donorRepository.findByUserId(5L)).thenReturn(Optional.of(donor));
        when(campRepository.findById(1L)).thenReturn(Optional.of(camp));
        when(registrationRepository.existsByCampIdAndDonorId(1L, 20L)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> campService.registerForCamp(1L, "donor@test.lk"));
    }
}
