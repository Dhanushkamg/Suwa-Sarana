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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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

    @Autowired
    private DonationSlotRepository slotRepository;

    @Autowired
    private SlotBookingRepository bookingRepository;

    private static final Set<String> CAMP_STATUSES = Set.of("SCHEDULED", "COMPLETED", "CANCELLED");

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
        camp.setRequiredBloodGroups(dto.getRequiredBloodGroups());

        DonationCamp saved = campRepository.save(camp);
        return mapToDto(saved);
    }

    public DonationCampDto updateCamp(Long id, CreateCampDto dto, String userEmail) {
        DonationCamp camp = campRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Camp not found"));

        if (!camp.getOrganizer().getEmail().equals(userEmail)) {
            throw ApiException.forbidden("Only the organizer can modify this camp.");
        }

        camp.setName(dto.getName());
        camp.setDistrict(dto.getDistrict());
        camp.setLocation(dto.getLocation());
        camp.setLatitude(dto.getLatitude());
        camp.setLongitude(dto.getLongitude());
        camp.setScheduledDate(dto.getScheduledDate());
        camp.setStartTime(dto.getStartTime());
        camp.setEndTime(dto.getEndTime());
        camp.setRequiredBloodGroups(dto.getRequiredBloodGroups());

        DonationCamp saved = campRepository.save(camp);
        return mapToDto(saved);
    }

    @Transactional
    public DonationCampDto updateCampStatus(Long id, String status, String userEmail) {
        DonationCamp camp = campRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Camp not found"));

        if (!camp.getOrganizer().getEmail().equals(userEmail)) {
            throw ApiException.forbidden("Only the organizer can modify this camp.");
        }

        String newStatus = status == null ? "" : status.trim().toUpperCase();
        if (!CAMP_STATUSES.contains(newStatus)) {
            throw ApiException.badRequest("Status must be one of SCHEDULED, COMPLETED, CANCELLED");
        }

        camp.setStatus(newStatus);
        DonationCamp saved = campRepository.save(camp);
        if (newStatus.equals("CANCELLED")) {
            cancelCampSlots(id);
        }
        return mapToDto(saved);
    }

    /** Cancels the camp's open/full slots and their active bookings, keeping bookedCount consistent. */
    private void cancelCampSlots(Long campId) {
        for (DonationSlot listed : slotRepository.findByCampIdOrderByStartTimeAsc(campId)) {
            if (listed.getStatus() != SlotStatus.OPEN && listed.getStatus() != SlotStatus.FULL) continue;

            // Lock like SlotBookingService does so a concurrent booking cannot slip in
            DonationSlot slot = slotRepository.findByIdForUpdate(listed.getId()).orElse(listed);
            List<SlotBooking> booked = bookingRepository.findBySlotIdAndStatus(slot.getId(), BookingStatus.BOOKED);
            for (SlotBooking booking : booked) {
                booking.setStatus(BookingStatus.CANCELLED);
                bookingRepository.save(booking);
            }
            slot.setBookedCount(Math.max(0, slot.getBookedCount() - booked.size()));
            slot.setStatus(SlotStatus.CANCELLED);
            slotRepository.save(slot);
        }
    }

    public List<DonationCampDto> getUpcomingCamps() {
        return campRepository.findByStatusOrderByScheduledDateAsc("SCHEDULED")
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<DonationCampDto> getRecommendedCamps(String donorEmail) {
        User user = userRepository.findByEmail(donorEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        DonorProfile donor = donorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Donor profile not found"));

        String donorBloodType = donor.getBloodType();

        return campRepository.findByStatusOrderByScheduledDateAsc("SCHEDULED").stream()
                .filter(camp -> {
                    String required = camp.getRequiredBloodGroups();
                    if (required == null || required.trim().isEmpty()) {
                        return true; // Open to all
                    }
                    return matchesBloodGroup(required, donorBloodType);
                })
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /** True if the donor's blood type is one of the comma-separated required groups (exact, case-insensitive). */
    private boolean matchesBloodGroup(String required, String donorBloodType) {
        if (donorBloodType == null || donorBloodType.trim().isEmpty()) {
            return false;
        }
        String donorGroup = donorBloodType.trim();
        return Arrays.stream(required.split(","))
                .anyMatch(group -> group.trim().equalsIgnoreCase(donorGroup));
    }

    public List<DonationCampDto> getMyCamps(String userEmail) {
        User organizer = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return campRepository.findByOrganizerIdOrderByScheduledDateDesc(organizer.getId()).stream()
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
        dto.setOrganizerName(DonationCamp.displayName(camp.getOrganizer()));
        dto.setRequiredBloodGroups(camp.getRequiredBloodGroups());
        return dto;
    }
}
