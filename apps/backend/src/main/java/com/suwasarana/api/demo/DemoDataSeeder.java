package com.suwasarana.api.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.camp.DonationCamp;
import com.suwasarana.api.camp.DonationCampRepository;
import com.suwasarana.api.donor.DeferralRepository;
import com.suwasarana.api.donor.DeferralRulesConfig;
import com.suwasarana.api.donor.DeferralService;
import com.suwasarana.api.donor.DonationRecord;
import com.suwasarana.api.donor.DonationRecordRepository;
import com.suwasarana.api.donor.DonationSource;
import com.suwasarana.api.donor.DonorDeferral;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.geo.District;
import com.suwasarana.api.inventory.InventoryMovementRepository;
import com.suwasarana.api.inventory.InventoryModel.BatchStatus;
import com.suwasarana.api.inventory.InventoryModel.Component;
import com.suwasarana.api.inventory.InventoryModel.Movement;
import com.suwasarana.api.inventory.InventoryModel.MovementReason;
import com.suwasarana.api.inventory.InventoryModel.Site;
import com.suwasarana.api.inventory.InventoryModel.Threshold;
import com.suwasarana.api.inventory.InventoryModel.Unit;
import com.suwasarana.api.inventory.InventorySiteRepository;
import com.suwasarana.api.inventory.InventoryThresholdRepository;
import com.suwasarana.api.inventory.InventoryUnitRepository;
import com.suwasarana.api.matching.BloodCompatibility;
import com.suwasarana.api.matching.MatchStatus;
import com.suwasarana.api.matching.RequestMatch;
import com.suwasarana.api.matching.RequestMatchRepository;
import com.suwasarana.api.notification.UserNotification;
import com.suwasarana.api.notification.UserNotificationRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.ReportReason;
import com.suwasarana.api.request.RequestReport;
import com.suwasarana.api.request.RequestReportRepository;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.slot.BookingStatus;
import com.suwasarana.api.slot.DonationSlot;
import com.suwasarana.api.slot.DonationSlotRepository;
import com.suwasarana.api.slot.SlotBooking;
import com.suwasarana.api.slot.SlotBookingRepository;
import com.suwasarana.api.slot.SlotStatus;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Realistic demo data for development: every role, all 25 districts, requests in every state, donation history,
 * camps with slots and bookings, blood-bank stock with low/critical examples and notifications, so the heatmap,
 * the admin dashboard and the donor/requester/blood-bank screens can be checked with non-zero numbers.
 *
 * <p><b>Dev only.</b> It exists only when the {@code dev} profile is active or {@code app.demo-data.enabled=true}
 * (never with {@code prod}), and it never runs against a database that already holds the demo accounts: the marker is
 * the admin account {@value #MARKER_EMAIL}. Running it twice is a no-op.
 *
 * <p><b>Passwords.</b> Every demo account gets the password in the environment variable {@code DEMO_PASSWORD}. If that
 * is unset the fixed development password is used only when the {@code dev} profile is active; otherwise nothing is
 * seeded. All demo e-mails end in {@code @demo.suwasarana.test} (a reserved, undeliverable domain) and phone numbers
 * are in the {@code 0710xxxxxx} range.
 *
 * <p>The data is fictional. Blood-type shares are illustrative, not NBTS statistics. Names are common Sri Lankan
 * names combined at random; hospital names are generic ("Base Hospital Kandy") and every institution account is
 * labelled "(Demo)".
 */
@org.springframework.stereotype.Component
@Conditional(DemoDataCondition.class)
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DOMAIN = "demo.suwasarana.test";
    public static final String MARKER_EMAIL = "admin@" + DOMAIN;
    /** Used only under the dev profile when DEMO_PASSWORD is not set. */
    static final String DEV_DEFAULT_PASSWORD = "DemoPass#2026";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    // Illustrative blood-type shares (percent), not official statistics
    private static final String[] TYPES = {"O+", "A+", "B+", "AB+", "O-", "A-", "B-", "AB-"};
    private static final int[] TYPE_WEIGHT = {36, 24, 26, 6, 3, 2, 2, 1};
    private static final String[] FIRST = {"Kasun", "Nimal", "Chamari", "Dilani", "Tharindu", "Sanduni", "Ruwan", "Anura", "Priya",
            "Suresh", "Kavitha", "Arun", "Nisha", "Rizwan", "Fathima", "Imran", "Ayesha", "Mohamed", "Hasini", "Janaka", "Sujeewa",
            "Thilini", "Lakmal", "Shanika", "Dinesh", "Mala", "Vijay", "Ramesh", "Sithara", "Pasan"};
    private static final String[] LAST = {"Perera", "Fernando", "Silva", "Jayasinghe", "Wickramasinghe", "Bandara", "Gunawardena",
            "Rajapaksha", "Dissanayake", "Kumar", "Selvam", "Nadarajah", "Rahman", "Hameed", "Samarasinghe", "Weerasekara"};
    private static final String[] HOSPITAL_KINDS = {"General Hospital", "Base Hospital", "Teaching Hospital", "District Hospital"};

    @Autowired private UserRepository userRepository;
    @Autowired private DonorRepository donorRepository;
    @Autowired private DeferralRepository deferralRepository;
    @Autowired private DonationRecordRepository donationRecordRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private RequestMatchRepository matchRepository;
    @Autowired private RequestReportRepository reportRepository;
    @Autowired private DonationCampRepository campRepository;
    @Autowired private DonationSlotRepository slotRepository;
    @Autowired private SlotBookingRepository bookingRepository;
    @Autowired private InventorySiteRepository siteRepository;
    @Autowired private InventoryUnitRepository unitRepository;
    @Autowired private InventoryThresholdRepository thresholdRepository;
    @Autowired private InventoryMovementRepository movementRepository;
    @Autowired private UserNotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ObjectMapper mapper;
    @Autowired private AppClock clock;
    @Autowired private Environment environment;

    @Value("${DEMO_PASSWORD:}")
    private String configuredPassword;

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Seeds once; returns what was created (empty when the demo data already exists or a password is missing). */
    public Map<String, Integer> seed() {
        if (userRepository.existsByEmail(MARKER_EMAIL)) {
            log.info("Demo data already present, nothing to do.");
            return Map.of();
        }
        String password = configuredPassword;
        if (password == null || password.isBlank()) {
            if (!environment.acceptsProfiles(Profiles.of("dev"))) {
                log.error("Demo data NOT seeded: set DEMO_PASSWORD (the built-in default is only allowed with the dev profile).");
                return Map.of();
            }
            password = DEV_DEFAULT_PASSWORD;
        }
        String hash = passwordEncoder.encode(password);
        log.warn("=== Seeding DEMO DATA (development only). Accounts end in @{} ===", DOMAIN);
        Map<String, Integer> counts = new TransactionTemplate(transactionManager).execute(status -> new Run(hash).execute());
        log.info("Demo data seeded: {}", counts);
        return counts;
    }

    // ===================================================================================== one seeding run

    private final class Run {
        final Random rnd = new Random(2026);
        final String hash;
        final LocalDateTime now = clock.utcNow(); // a moment (UTC wall clock), like every created/expires/closed timestamp
        final LocalDate today = clock.today();
        int phoneSeq = 100000;
        final Map<String, Integer> counts = new LinkedHashMap<>();

        User admin;
        final List<User> hospitals = new ArrayList<>();      // verified hospitals
        final List<User> bloodBanks = new ArrayList<>();     // verified blood banks
        final List<User> institutions = new ArrayList<>();   // verified hospitals + blood banks
        final List<User> requesters = new ArrayList<>();     // verified requesters
        final List<User> pendingUsers = new ArrayList<>();
        final List<DonorProfile> donors = new ArrayList<>();
        final Set<Long> donorsWithDonationInWindow = new HashSet<>();

        Run(String hash) {
            this.hash = hash;
        }

        Map<String, Integer> execute() {
            seedUsers();
            seedDonors();
            seedDonationHistory();
            seedRequests();
            seedCampsAndSlots();
            seedInventory();
            seedReports();
            seedNotifications();
            donorRepository.saveAll(donors);
            return counts;
        }

        void count(String key, int n) {
            counts.merge(key, n, Integer::sum);
        }

        // ------------------------------------------------------------------ users

        User user(String local, String first, String last, Role role, VerificationStatus status) {
            User u = new User();
            u.setEmail(local + "@" + DOMAIN);
            u.setPhoneNumber(String.format("0710%06d", phoneSeq++));
            u.setFirstName(first);
            u.setLastName(last);
            u.setPasswordHash(hash);
            u.setRole(role);
            u.setVerificationStatus(status);
            u.setEmailVerifiedAt(clock.instant()); // demo mailboxes count as confirmed: institutions need that to publish (VerificationGuard)
            u.setCreatedAt(now.minusDays(40 + rnd.nextInt(60)));
            u.setUpdatedAt(now);
            count("users", 1);
            return userRepository.save(u);
        }

        void seedUsers() {
            admin = user("admin", "Demo", "Admin", Role.ADMIN, VerificationStatus.VERIFIED);

            String[] hospitalDistricts = {"Colombo", "Kandy", "Galle", "Jaffna", "Anuradhapura", "Batticaloa", "Kurunegala", "Ratnapura"};
            for (int i = 0; i < hospitalDistricts.length; i++) {
                String d = hospitalDistricts[i];
                User h = user("hospital" + (i + 1), HOSPITAL_KINDS[i % HOSPITAL_KINDS.length] + " " + d, "(Demo)",
                        Role.HOSPITAL_REQUESTER, VerificationStatus.VERIFIED);
                hospitals.add(h);
                districtOfInstitution.put(h.getId(), d);
            }
            String[] bankDistricts = {"Colombo", "Kandy", "Matara", "Trincomalee"};
            for (int i = 0; i < bankDistricts.length; i++) {
                User b = user("bloodbank" + (i + 1), "Blood Bank " + bankDistricts[i], "(Demo)",
                        Role.BLOOD_BANK_REQUESTER, VerificationStatus.VERIFIED);
                bloodBanks.add(b);
                districtOfInstitution.put(b.getId(), bankDistricts[i]);
            }
            institutions.addAll(hospitals);
            institutions.addAll(bloodBanks);

            // One awaiting verification, one rejected (so the admin queue and the rejected state have examples)
            pendingUsers.add(user("hospital.pending", "Private Hospital Gampaha", "(Demo)", Role.HOSPITAL_REQUESTER, VerificationStatus.PENDING));
            pendingUsers.add(user("bloodbank.rejected", "Blood Bank Vavuniya", "(Demo)", Role.BLOOD_BANK_REQUESTER, VerificationStatus.REJECTED));

            for (int i = 1; i <= 4; i++) {
                requesters.add(user("requester" + i, pick(FIRST), pick(LAST), Role.REQUESTER, VerificationStatus.VERIFIED));
            }
            pendingUsers.add(user("requester.pending1", pick(FIRST), pick(LAST), Role.REQUESTER, VerificationStatus.PENDING));
            pendingUsers.add(user("requester.pending2", pick(FIRST), pick(LAST), Role.REQUESTER, VerificationStatus.PENDING));
        }

        final Map<Long, String> districtOfInstitution = new LinkedHashMap<>();

        // ------------------------------------------------------------------ donors

        void seedDonors() {
            List<String> weighted = new ArrayList<>();
            for (String d : District.names()) {
                int w = switch (d) {
                    case "Colombo" -> 6;
                    case "Gampaha", "Kandy", "Kurunegala" -> 3;
                    case "Galle", "Kalutara", "Ratnapura" -> 2;
                    default -> 1;
                };
                for (int i = 0; i < w; i++) weighted.add(d);
            }
            for (int i = 0; i < 60; i++) {
                // The first 25 donors cover every district once; the rest follow population weight
                String district = i < 25 ? District.names().get(i) : weighted.get(rnd.nextInt(weighted.size()));
                User u = user("donor" + (i + 1), pick(FIRST), pick(LAST), Role.DONOR, VerificationStatus.VERIFIED);
                District.Entry c = District.get(district);

                DonorProfile p = new DonorProfile();
                p.setUser(u);
                p.setBloodType(weightedType());
                int age = i == 7 ? 57 : 19 + rnd.nextInt(38);        // donor 8: a 57-year-old first-time donor (not eligible)
                p.setDateOfBirth(today.minusYears(age).minusDays(rnd.nextInt(300)));
                p.setWeightKg(BigDecimal.valueOf(52 + rnd.nextInt(40)).add(BigDecimal.valueOf(rnd.nextInt(10), 1)));
                p.setDistrict(district);
                p.setLatitude(round(c.latitude() + (rnd.nextDouble() - 0.5) * 0.12));
                p.setLongitude(round(c.longitude() + (rnd.nextDouble() - 0.5) * 0.12));
                p.setAvailable(rnd.nextInt(100) < 88);
                p.setReliabilityScore(BigDecimal.valueOf(40 + rnd.nextInt(56)).setScale(2));
                if (rnd.nextInt(5) == 0) {
                    p.setQuietHoursStart(LocalTime.of(22, 0));
                    p.setQuietHoursEnd(LocalTime.of(6, 0));
                }
                p.setTotalDonations(0);
                p.setLivesHelpedEstimate(0);
                donors.add(donorRepository.save(p));
            }
            count("donorProfiles", donors.size());
        }

        // ------------------------------------------------------------------ older donation history

        void seedDonationHistory() {
            // Donations 5 to 20 months ago: visible in history and totals, no active deferral any more
            for (int i = 0; i < donors.size(); i++) {
                if (i == 7) continue; // the first-time-only donor
                int n = rnd.nextInt(5);
                for (int k = 0; k < n; k++) {
                    LocalDate on = today.minusMonths(5L + k * 4L + rnd.nextInt(3)).minusDays(rnd.nextInt(20));
                    record(donors.get(i), on, DonationSource.MANUAL, null, null, null, null, null, null);
                }
            }
        }

        /** Same side effects as DonationRecorder, with a chosen date: history row, totals, and a deferral while the interval runs. */
        DonationRecord record(DonorProfile d, LocalDate on, DonationSource source, BloodRequest request, RequestMatch match,
                              SlotBooking booking, User host, String hostName, String district) {
            DonationRecord r = new DonationRecord();
            r.setDonorId(d.getId());
            r.setDonatedOn(on);
            r.setSource(source);
            r.setRequestId(request == null ? null : request.getId());
            r.setMatchId(match == null ? null : match.getId());
            r.setBookingId(booking == null ? null : booking.getId());
            r.setHostUserId(host == null ? null : host.getId());
            r.setHostName(hostName);
            r.setDistrict(district == null ? d.getDistrict() : district);
            r.setBloodType(d.getBloodType());
            r.setCreatedAt(on.atStartOfDay(clock.zone()).toInstant());
            donationRecordRepository.save(r);
            d.setTotalDonations(d.getTotalDonations() + 1);
            d.setLivesHelpedEstimate(d.getLivesHelpedEstimate() + 3);
            if (!DeferralRulesConfig.nextEligibleDateAfterDonation(on).isBefore(today)) {
                DonorDeferral f = new DonorDeferral();
                f.setDonor(d);
                f.setReason(DeferralService.RECENT_DONATION);
                f.setDeferredFrom(on);
                f.setDeferredUntil(DeferralRulesConfig.deferralEndAfterDonation(on));
                deferralRepository.save(f);
                donorsWithDonationInWindow.add(d.getId());
            }
            donorRepository.save(d);
            count("donations", 1);
            return r;
        }

        // ------------------------------------------------------------------ requests, matches, donations

        void seedRequests() {
            List<RequestStatus> plan = new ArrayList<>();
            addN(plan, RequestStatus.OPEN, 4);
            addN(plan, RequestStatus.ESCALATING, 6);
            addN(plan, RequestStatus.MATCHED, 4);
            addN(plan, RequestStatus.FULFILLED, 14);
            addN(plan, RequestStatus.CANCELLED, 6);
            addN(plan, RequestStatus.EXPIRED, 8);
            java.util.Collections.shuffle(plan, rnd);

            List<User> owners = new ArrayList<>(requesters);
            owners.addAll(hospitals);
            Set<Long> donatedAlready = new HashSet<>();

            for (int i = 0; i < plan.size(); i++) {
                RequestStatus st = plan.get(i);
                // The first 25 requests cover every district once
                String district = i < 25 ? District.names().get(i) : District.names().get(rnd.nextInt(25));
                District.Entry c = District.get(district);
                User owner = i % 9 == 4 ? pendingUsers.get(2 + rnd.nextInt(2)) : owners.get(rnd.nextInt(owners.size()));

                BloodRequest r = new BloodRequest();
                r.setRequester(owner);
                r.setPatientBloodType(weightedType());
                r.setUrgency(owner.getVerificationStatus() == VerificationStatus.VERIFIED ? Urgency.values()[rnd.nextInt(3)]
                        : Urgency.values()[rnd.nextInt(2)]); // unverified accounts cannot raise CRITICAL requests
                r.setUnitsNeeded((short) (1 + rnd.nextInt(r.getUrgency() == Urgency.CRITICAL ? 3 : 4)));
                r.setHospitalName(HOSPITAL_KINDS[rnd.nextInt(HOSPITAL_KINDS.length)] + " " + district);
                r.setDistrict(district);
                r.setLatitude(round(c.latitude() + (rnd.nextDouble() - 0.5) * 0.08));
                r.setLongitude(round(c.longitude() + (rnd.nextDouble() - 0.5) * 0.08));
                r.setStatus(st);
                r.setCurrentRadiusKm((short) (5 + 10 * rnd.nextInt(4)));

                boolean active = st.isActive();
                LocalDateTime created = active ? now.minusMinutes(10 + rnd.nextInt(280))
                        : now.minusDays(1 + rnd.nextInt(27)).minusHours(rnd.nextInt(20));
                r.setCreatedAt(created);
                r.setLastEscalatedAt(created.plusMinutes(5L * (1 + rnd.nextInt(3))));
                r.setExpiresAt(active ? now.plusMinutes(40 + rnd.nextInt(260)) : created.plusHours(6));
                switch (st) {
                    case FULFILLED -> r.setClosedAt(created.plusHours(1 + rnd.nextInt(5)));
                    case CANCELLED -> {
                        r.setClosedAt(created.plusMinutes(10 + rnd.nextInt(110)));
                        r.setCloseReason(new String[]{"NO_LONGER_NEEDED", "FOUND_DONOR_ELSEWHERE", "OTHER"}[rnd.nextInt(3)]);
                    }
                    case EXPIRED -> r.setClosedAt(r.getExpiresAt());
                    case MATCHED -> r.setMatchedAt(now.minusMinutes(5 + rnd.nextInt(100)));
                    default -> { }
                }
                if (owner.getVerificationStatus() != VerificationStatus.VERIFIED) {
                    r.setFraudRiskScore(25 + rnd.nextInt(45));
                    r.setAiFlagReason("Account not verified; check the hospital with the ward before approving.");
                    r.setTriagedAt(created.plusSeconds(2));
                } else {
                    r.setFraudRiskScore(rnd.nextInt(12));
                    r.setTriagedAt(created.plusSeconds(2));
                }
                requestRepository.save(r);
                count("requests", 1);
                seedMatches(r, st, donatedAlready);
            }
        }

        void seedMatches(BloodRequest r, RequestStatus st, Set<Long> donatedAlready) {
            List<DonorProfile> candidates = donors.stream()
                    .filter(d -> BloodCompatibility.canDonate(d.getBloodType(), r.getPatientBloodType()))
                    .sorted(Comparator.comparingDouble(d -> km(d, r)))
                    .limit(3 + rnd.nextInt(4))
                    .toList();
            int accepted = 0;
            for (DonorProfile d : candidates) {
                RequestMatch m = new RequestMatch();
                m.setRequest(r);
                m.setDonor(d);
                m.setDistanceKm(BigDecimal.valueOf(km(d, r)).setScale(2, RoundingMode.HALF_UP));
                m.setCreatedAt(r.getCreatedAt().plusMinutes(1));
                MatchStatus ms;
                boolean donate = false;
                switch (st) {
                    case FULFILLED -> {
                        if (accepted < r.getUnitsNeeded() && !donatedAlready.contains(d.getId())) {
                            ms = MatchStatus.ACCEPTED; accepted++; donate = true;
                        } else ms = rnd.nextBoolean() ? MatchStatus.DECLINED : MatchStatus.NO_RESPONSE;
                    }
                    case MATCHED -> ms = accepted < r.getUnitsNeeded() ? MatchStatus.ACCEPTED : MatchStatus.PENDING;
                    case OPEN, ESCALATING -> ms = rnd.nextInt(3) == 0 ? MatchStatus.DECLINED : MatchStatus.PENDING;
                    default -> ms = rnd.nextBoolean() ? MatchStatus.NO_RESPONSE : MatchStatus.DECLINED;
                }
                if (st == RequestStatus.MATCHED && ms == MatchStatus.ACCEPTED) accepted++;
                m.setStatus(ms);
                if (ms != MatchStatus.PENDING && ms != MatchStatus.NO_RESPONSE) {
                    m.setRespondedAt(r.getCreatedAt().plusMinutes(3 + rnd.nextInt(25)));
                }
                if (donate) m.setDonated(true);
                matchRepository.save(m);
                count("matches", 1);
                if (donate) {
                    donatedAlready.add(d.getId());
                    record(d, r.getClosedAt().toLocalDate(), DonationSource.MATCH, r, m, null, r.getRequester(),
                            r.getHospitalName(), r.getDistrict());
                }
            }
        }

        // ------------------------------------------------------------------ camps, slots, bookings

        void seedCampsAndSlots() {
            String[] campDistricts = {"Colombo", "Gampaha", "Kandy", "Galle", "Kurunegala", "Jaffna", "Badulla", "Matara", "Anuradhapura", "Ampara"};
            List<User> organisers = new ArrayList<>(institutions);
            for (int i = 0; i < campDistricts.length + 2; i++) {
                boolean past = i >= campDistricts.length; // two finished camps with check-ins
                String d = campDistricts[i % campDistricts.length];
                District.Entry c = District.get(d);
                User org = organisers.get(i % organisers.size());

                DonationCamp camp = new DonationCamp();
                camp.setName("Community Blood Drive " + d);
                camp.setDistrict(d);
                camp.setLocation((i % 2 == 0 ? "Town Hall, " : "Temple Grounds, ") + d);
                camp.setLatitude(round(c.latitude() + (rnd.nextDouble() - 0.5) * 0.05));
                camp.setLongitude(round(c.longitude() + (rnd.nextDouble() - 0.5) * 0.05));
                camp.setScheduledDate(past ? today.minusDays(3L + i % 3) : today.plusDays(1L + i * 2L));
                camp.setStartTime(LocalTime.of(9, 0));
                camp.setEndTime(LocalTime.of(15, 0));
                camp.setOrganizer(org);
                camp.setStatus(past ? "COMPLETED" : "SCHEDULED");
                camp.setRequiredBloodGroups(i % 4 == 1 ? "O-,A-,B-" : null);
                campRepository.save(camp);
                count("camps", 1);

                List<DonorProfile> pool = donors.stream().filter(p -> p.getDistrict().equals(d)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                for (DonorProfile p : donors) if (pool.size() < 8 && !pool.contains(p)) pool.add(p);
                int poolIdx = 0;
                for (int h = 9; h < 15; h++) {
                    DonationSlot s = new DonationSlot();
                    s.setCamp(camp);
                    s.setStartTime(camp.getScheduledDate().atTime(h, 0));
                    s.setEndTime(camp.getScheduledDate().atTime(h + 1, 0));
                    s.setCapacity(4);
                    s.setBookedCount(0);
                    s.setStatus(SlotStatus.OPEN);
                    slotRepository.save(s);
                    count("slots", 1);
                    int bookings = h < 12 ? 1 + rnd.nextInt(3) : rnd.nextInt(2);
                    for (int b = 0; b < bookings && poolIdx < pool.size(); b++) {
                        DonorProfile donor = pool.get(poolIdx++);
                        SlotBooking sb = new SlotBooking();
                        sb.setSlot(s);
                        sb.setDonor(donor);
                        sb.setBookedAt(clock.instant().minusSeconds(3600L * (1 + rnd.nextInt(60))));
                        sb.setEligibleAsOf(camp.getScheduledDate());
                        if (past) {
                            sb.setStatus(BookingStatus.CHECKED_IN);
                            sb.setCheckedInAt(s.getStartTime().atZone(clock.zone()).toInstant().plusSeconds(600));
                        } else {
                            sb.setStatus(BookingStatus.BOOKED);
                        }
                        bookingRepository.save(sb);
                        count("bookings", 1);
                        s.setBookedCount(s.getBookedCount() + 1);
                        if (past) {
                            record(donor, camp.getScheduledDate(), DonationSource.SLOT_CHECKIN, null, null, sb, org, camp.getName(), d);
                        }
                    }
                    if (s.getBookedCount() >= s.getCapacity()) s.setStatus(SlotStatus.FULL);
                    if (past) s.setStatus(SlotStatus.COMPLETED);
                    slotRepository.save(s);
                }
            }
            // Walk-in windows tomorrow at two hospitals
            for (int i = 0; i < 2; i++) {
                User h = hospitals.get(i);
                for (int hour = 10; hour < 13; hour++) {
                    DonationSlot s = new DonationSlot();
                    s.setHospitalUser(h);
                    s.setStartTime(today.plusDays(1).atTime(hour, 0));
                    s.setEndTime(today.plusDays(1).atTime(hour, 30));
                    s.setCapacity(3);
                    s.setBookedCount(0);
                    s.setStatus(SlotStatus.OPEN);
                    slotRepository.save(s);
                    count("slots", 1);
                }
            }
        }

        // ------------------------------------------------------------------ inventory

        void seedInventory() {
            Instant nowInstant = clock.instant();
            for (int i = 0; i < institutions.size(); i++) {
                User inst = institutions.get(i);
                String d = districtOfInstitution.get(inst.getId());
                District.Entry c = District.get(d);

                Site site = new Site();
                site.setOwnerUserId(inst.getId());
                site.setSiteName(DonationCamp.displayName(inst));
                site.setDistrict(d);
                site.setLatitude(c.latitude());
                site.setLongitude(c.longitude());
                site.setPublicSignals(true);
                // Fresh within the 48 h rule, except the last institution whose record is 4 days old (hidden from the public ticker)
                site.setConfirmedAt(i == institutions.size() - 1 ? nowInstant.minusSeconds(4 * 86400L) : nowInstant.minusSeconds(3600L * (1 + rnd.nextInt(20))));
                site.setUpdatedAt(nowInstant);
                siteRepository.save(site);
                count("inventorySites", 1);

                for (String type : TYPES) {
                    Threshold t = new Threshold();
                    t.setOwnerUserId(inst.getId());
                    t.setBloodType(type);
                    t.setComponent(Component.WHOLE_BLOOD);
                    t.setLowThreshold(12);
                    t.setCriticalThreshold(5);
                    t.setUpdatedAt(nowInstant);
                    thresholdRepository.save(t);

                    int units = 14 + rnd.nextInt(30);
                    // Deliberate shortages: the exact blood types and institutions are demo choices, not real stock
                    if (i == 0 && type.equals("O-")) units = 3;          // critical
                    if (i == 0 && type.equals("A-")) units = 9;          // low
                    if (i == 1 && type.equals("A-")) units = 8;          // low
                    if (i == 2 && type.equals("O+")) units = 4;          // critical
                    if (i == 4 && type.equals("B-")) units = 2;          // critical
                    if (i == institutions.size() - 1 && type.equals("O+")) units = 1; // critical but stale: not shown publicly
                    int age = 2 + rnd.nextInt(8);
                    addBatch(inst, type, units, age, today.minusDays(age).plusDays(35));
                    count("thresholds", 1);
                }
                if (i == 2) addBatch(inst, "B+", 6, 33, today.plusDays(2));    // 6 units expire in 2 days (EXPIRING_SOON)
                if (i == 3) addBatch(inst, "AB+", 5, 36, today.minusDays(1)); // already expired: the expiry job marks it
                addPlatelets(inst, i);
            }
        }

        void addBatch(User inst, String type, int units, int collectedDaysAgo, LocalDate expiresOn) {
            Unit u = new Unit();
            u.setOwnerUserId(inst.getId());
            u.setBloodType(type);
            u.setComponent(Component.WHOLE_BLOOD);
            u.setUnits(units);
            LocalDate collected = today.minusDays(collectedDaysAgo);
            u.setCollectedOn(collected);
            u.setExpiresOn(expiresOn); // demo dates only; real shelf lives are entered by the institution
            u.setStatus(BatchStatus.ACTIVE);
            u.setCreatedAt(clock.instant());
            u.setUpdatedAt(clock.instant());
            unitRepository.save(u);
            Movement m = new Movement();
            m.setOwnerUserId(inst.getId());
            m.setUnitId(u.getId());
            m.setBloodType(type);
            m.setComponent(Component.WHOLE_BLOOD);
            m.setDelta(units);
            m.setReason(MovementReason.COLLECTION);
            m.setCreatedBy(inst.getId());
            m.setCreatedAt(collected.atTime(10, 0).atZone(clock.zone()).toInstant());
            movementRepository.save(m);
            count("stockBatches", 1);
        }

        void addPlatelets(User inst, int i) {
            if (i % 3 != 0) return;
            Unit u = new Unit();
            u.setOwnerUserId(inst.getId());
            u.setBloodType("O+");
            u.setComponent(Component.PLATELETS);
            u.setUnits(6 + rnd.nextInt(10));
            u.setCollectedOn(today.minusDays(1));
            u.setExpiresOn(today.plusDays(4));
            u.setStatus(BatchStatus.ACTIVE);
            u.setCreatedAt(clock.instant());
            u.setUpdatedAt(clock.instant());
            unitRepository.save(u);
            count("stockBatches", 1);
        }

        // ------------------------------------------------------------------ reports

        void seedReports() {
            List<BloodRequest> flagged = requestRepository.findAll().stream()
                    .filter(r -> r.getRequester().getVerificationStatus() != VerificationStatus.VERIFIED).limit(2).toList();
            int n = 0;
            for (BloodRequest r : flagged) {
                RequestReport rep = new RequestReport();
                rep.setRequest(r);
                rep.setReportedBy(donors.get(n++).getUser());
                rep.setReasonCode(ReportReason.FAKE_OR_MISLEADING.name());
                rep.setDetails("The hospital said they have no such patient.");
                rep.setReason(ReportReason.FAKE_OR_MISLEADING.name() + ": " + rep.getDetails());
                rep.setCreatedAt(clock.instant().minusSeconds(3600L * (2 + n)));
                reportRepository.save(rep);
                count("reports", 1);
            }
        }

        // ------------------------------------------------------------------ notifications

        void seedNotifications() {
            // Match alerts for the first donors on their most recent matches, one read and the rest unread
            int shown = 0;
            for (DonorProfile d : donors) {
                if (shown >= 12) break;
                List<RequestMatch> mine = matchRepository.findByDonorIdOrderByCreatedAtDesc(d.getId());
                if (mine.isEmpty()) continue;
                int idx = 0;
                for (RequestMatch m : mine.stream().limit(2).toList()) {
                    BloodRequest r = m.getRequest();
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("requestId", r.getId());
                    params.put("bloodType", r.getPatientBloodType());
                    params.put("hospital", r.getHospitalName());
                    params.put("district", r.getDistrict());
                    params.put("urgency", r.getUrgency().name());
                    params.put("units", r.getUnitsNeeded());
                    notification(d.getUser(), "NEW_MATCH", "notifications.match.title", "notifications.match.body", params, r.getId(),
                            m.getCreatedAt(), idx++ > 0);
                }
                shown++;
            }
            // Requesters: accepted donors and fulfilled requests
            for (BloodRequest r : requestRepository.findAll()) {
                if (r.getStatus() == RequestStatus.MATCHED) {
                    notification(r.getRequester(), "MATCH_ACCEPTED", "notifications.accepted.title", "notifications.accepted.body",
                            Map.of("requestId", r.getId(), "hospital", r.getHospitalName(), "accepted", r.getUnitsNeeded(), "needed", r.getUnitsNeeded()),
                            r.getId(), r.getMatchedAt(), false);
                } else if (r.getStatus() == RequestStatus.FULFILLED && rnd.nextInt(3) == 0) {
                    notification(r.getRequester(), "REQUEST_FULFILLED", "notifications.fulfilled.title", "notifications.fulfilled.body",
                            Map.of("requestId", r.getId(), "hospital", r.getHospitalName()), r.getId(), r.getClosedAt(), true);
                }
            }
            // Blood banks: low-stock alerts for the demo shortages
            for (int i : new int[]{0, 1, 2, 4}) {
                User inst = institutions.get(i);
                String type = i == 0 ? "O-" : i == 1 ? "A-" : i == 2 ? "O+" : "B-";
                int units = i == 0 ? 3 : i == 1 ? 8 : i == 2 ? 4 : 2;
                notification(inst, "LOW_STOCK", "notifications.lowStock.title", "notifications.lowStock.body",
                        Map.of("bloodType", type, "component", "WHOLE_BLOOD", "status", units <= 5 ? "CRITICAL" : "LOW", "units", units),
                        null, now.minusHours(2 + i), false);
            }
        }

        void notification(User user, String type, String titleKey, String bodyKey, Map<String, Object> params, Long ref,
                          LocalDateTime at, boolean read) {
            UserNotification n = new UserNotification();
            n.setUserId(user.getId());
            n.setType(type);
            n.setTitleKey(titleKey);
            n.setBodyKey(bodyKey);
            try {
                n.setParams(mapper.writeValueAsString(params));
            } catch (Exception e) {
                n.setParams("{}");
            }
            n.setReferenceId(ref);
            Instant created = at.toInstant(java.time.ZoneOffset.UTC);
            n.setCreatedAt(created);
            if (read) n.setReadAt(created.plusSeconds(600));
            notificationRepository.save(n);
            count("notifications", 1);
        }

        // ------------------------------------------------------------------ helpers

        String pick(String[] a) {
            return a[rnd.nextInt(a.length)];
        }

        String weightedType() {
            int roll = rnd.nextInt(100), acc = 0;
            for (int i = 0; i < TYPES.length; i++) {
                acc += TYPE_WEIGHT[i];
                if (roll < acc) return TYPES[i];
            }
            return "O+";
        }

        void addN(List<RequestStatus> list, RequestStatus s, int n) {
            for (int i = 0; i < n; i++) list.add(s);
        }

        double round(double v) {
            return Math.round(v * 10000.0) / 10000.0;
        }

        double km(DonorProfile d, BloodRequest r) {
            double dLat = Math.toRadians(r.getLatitude() - d.getLatitude());
            double dLon = Math.toRadians(r.getLongitude() - d.getLongitude());
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(d.getLatitude()))
                    * Math.cos(Math.toRadians(r.getLatitude())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        }
    }
}
