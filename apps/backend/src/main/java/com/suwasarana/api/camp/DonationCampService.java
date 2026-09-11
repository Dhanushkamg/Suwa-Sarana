package com.suwasarana.api.camp;

import com.suwasarana.api.camp.dto.CreateCampDto;
import com.suwasarana.api.camp.dto.DonationCampDto;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DonationCampService {

    @Autowired
    private DonationCampRepository campRepository;

    @Autowired
    private CampRegistrationRepository registrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DonorRepository donorRepository;

    public DonationCampDto createCamp(CreateCampDto dto, String userEmail) {
        User organizer = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Organizer not found"));

        DonationCamp camp = new DonationCamp();
        camp.setName(dto.getName());
        camp.setDistrict(dto.getDistrict());
        camp.setLocation(dto.getLocation());
        camp.setLatitude(dto.getLatitude());
        camp.setLongitude(dto.getLongitude());
        camp.setScheduledDate(dto.getScheduledDate());
        camp.setStartTime(dto.getStartTime());
        camp.setEndTime(dto.getEndTime());
        camp.setOrganizer(organizer);

        DonationCamp saved = campRepository.save(camp);
        return mapToDto(saved);
    }

    public List<DonationCampDto> getUpcomingCamps() {
        return campRepository.findByStatusOrderByScheduledDateAsc("SCHEDULED")
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public void registerForCamp(Long campId, String donorEmail) {
        User user = userRepository.findByEmail(donorEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        DonorProfile donor = donorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Donor profile not found"));

        DonationCamp camp = campRepository.findById(campId)
                .orElseThrow(() -> new RuntimeException("Camp not found"));

        if (registrationRepository.existsByCampIdAndDonorId(campId, donor.getId())) {
            throw new RuntimeException("Already registered");
        }

        CampRegistration registration = new CampRegistration();
        registration.setCamp(camp);
        registration.setDonor(donor);
        registrationRepository.save(registration);
    }

    private DonationCampDto mapToDto(DonationCamp camp) {
        DonationCampDto dto = new DonationCampDto();
        dto.setId(camp.getId());
        dto.setName(camp.getName());
        dto.setDistrict(camp.getDistrict());
        dto.setLocation(camp.getLocation());
        dto.setLatitude(camp.getLatitude());
        dto.setLongitude(camp.getLongitude());
        dto.setScheduledDate(camp.getScheduledDate());
        dto.setStartTime(camp.getStartTime());
        dto.setEndTime(camp.getEndTime());
        dto.setStatus(camp.getStatus());
        dto.setOrganizerName(camp.getOrganizer().getEmail());
        return dto;
    }
}
