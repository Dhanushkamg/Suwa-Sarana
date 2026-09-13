package com.suwasarana.api.camp;

import com.suwasarana.api.camp.dto.CreateCampDto;
import com.suwasarana.api.camp.dto.DonationCampDto;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private DonationCampService campService;

    @Test
    public void testCreateCamp_Success() {
        User organizer = new User();
        organizer.setId(10L);
        organizer.setEmail("organizer@hospital.lk");

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
        assertEquals("organizer@hospital.lk", result.getOrganizerName());
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
