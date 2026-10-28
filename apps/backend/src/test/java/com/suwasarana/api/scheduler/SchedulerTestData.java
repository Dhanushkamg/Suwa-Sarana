package com.suwasarana.api.scheduler;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Small builders shared by the scheduler tests (H2, schema generated from the entities). */
final class SchedulerTestData {

    private SchedulerTestData() {
    }

    /** Empties the tables the scheduler tests touch (children first). */
    static void clean(JdbcTemplate jdbc) {
        for (String table : new String[] {"slot_bookings", "donation_slots", "circle_responses", "request_circles", "notification_logs",
                "request_matches", "request_reports", "blood_requests", "refresh_tokens", "otp_verifications", "contact_messages"}) {
            try {
                jdbc.update("DELETE FROM " + table);
            } catch (RuntimeException ignored) {
                // table not present in this schema
            }
        }
    }

    static User user(UserRepository users, Role role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User u = new User();
        u.setEmail("sched-" + unique + "@test.com");
        u.setPhoneNumber("07" + String.format("%08d", Math.abs(unique.hashCode()) % 100_000_000));
        u.setFirstName("Sched");
        u.setLastName("Test");
        u.setPasswordHash("x");
        u.setRole(role);
        return users.save(u);
    }

    static DonorProfile donor(UserRepository users, DonorRepository donors) {
        DonorProfile d = new DonorProfile();
        d.setUser(user(users, Role.DONOR));
        d.setBloodType("O+");
        d.setDateOfBirth(LocalDate.of(1995, 5, 5));
        d.setWeightKg(new BigDecimal("65.00"));
        d.setDistrict("Colombo");
        d.setLatitude(6.93);
        d.setLongitude(79.85);
        return donors.save(d);
    }

    static BloodRequest request(RequestRepository requests, User owner, RequestStatus status, LocalDateTime expiresAt) {
        BloodRequest r = new BloodRequest();
        r.setRequester(owner);
        r.setPatientBloodType("A+");
        r.setUnitsNeeded((short) 1);
        r.setUrgency(Urgency.URGENT);
        r.setHospitalName("Test Hospital");
        r.setDistrict("Colombo");
        r.setLatitude(6.9);
        r.setLongitude(79.8);
        r.setStatus(status);
        r.setExpiresAt(expiresAt);
        return requests.save(r);
    }
}
