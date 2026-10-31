package com.suwasarana.api.camp;

import com.suwasarana.api.camp.dto.CreateCampDto;
import com.suwasarana.api.camp.dto.DonationCampDto;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.geo.District;
import com.suwasarana.api.matching.BloodCompatibility;
import com.suwasarana.api.security.VerificationGuard;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.common.ErrorCodes;
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
@Transactional
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

    @Autowired
    private AppClock clock;

    @Autowired
    private VerificationGuard verificationGuard;

    private static final Set<String> CAMP_STATUSES = Set.of("SCHEDULED", "COMPLETED", "CANCELLED");

    /**
     * Shared by create and update. A camp must end after it starts, take place in one of the 25 districts, list only
     * valid blood groups, and (when it is new or its date changes) not be in the past.
     * Returns the canonical district name.
     */
    private String validate(CreateCampDto dto, LocalDate currentDate) {
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw ApiException.badRequest(ErrorCodes.TIME_RANGE_INVALID, "End time must be after start time");
        }
        boolean dateChanged = currentDate == null || !currentDate.equals(dto.getScheduledDate());
        if (dateChanged && dto.getScheduledDate().isBefore(clock.today())) {
            throw ApiException.badRequest(ErrorCodes.CAMP_IN_PAST, "A camp cannot be scheduled in the past");
        }
        if (dto.getRequiredBloodGroups() != null && !dto.getRequiredBloodGroups().isBlank()) {
            for (String g : dto.getRequiredBloodGroups().split(",")) {
                if (!BloodCompatibility.isValid(g.trim().toUpperCase())) {
                    throw ApiException.badRequest(ErrorCodes.BLOOD_GROUP_INVALID,
                            "Unknown blood group '" + g.trim() + "'. Use A+, A-, B+, B-, AB+, AB-, O+ or O-.");
                }
            }
        }
        return District.normalise(dto.getDistrict())
                .orElseThrow(() -> ApiException.badRequest(ErrorCodes.UNKNOWN_DISTRICT,
                        "Unknown district. Use one of the 25 official district names (GET /api/meta/districts)."));
    }

    public DonationCampDto createCamp(CreateCampDto dto, String userEmail) {
        User organizer = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> ApiException.notFound("Organizer not found"));
        // Hospitals / blood banks must be VERIFIED with a confirmed email before anything is published to donors.
        verificationGuard.requireVerified(organizer);
        String district = validate(dto, null);

        DonationCamp camp = new DonationCamp();
        camp.setName(dto.getName().trim());
        camp.setDistrict(district);
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
        verificationGuard.requireVerified(camp.getOrganizer());
        String district = validate(dto, camp.getScheduledDate());

        camp.setName(dto.getName().trim());
        camp.setDistrict(district);
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
        verificationGuard.requireVerified(camp.getOrganizer());

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
        // Only camps that have not happened yet
        return campRepository.findUpcoming("SCHEDULED", clock.today())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<DonationCampDto> getRecommendedCamps(String donorEmail) {
        User user = userRepository.findByEmail(donorEmail)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        DonorProfile donor = donorRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.DONOR_PROFILE_NOT_FOUND, "Donor profile not found"));

        String donorBloodType = donor.getBloodType();

        return campRepository.findUpcoming("SCHEDULED", clock.today()).stream()
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
                .orElseThrow(() -> ApiException.notFound("User not found"));
        
        DonorProfile donor = donorRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.DONOR_PROFILE_NOT_FOUND, "Donor profile not found"));

        DonationCamp camp = campRepository.findById(campId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.CAMP_NOT_FOUND, "Camp not found"));

        if (registrationRepository.existsByCampIdAndDonorId(campId, donor.getId())) {
            throw ApiException.conflict("Already registered");
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
