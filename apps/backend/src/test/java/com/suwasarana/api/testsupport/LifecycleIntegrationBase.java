package com.suwasarana.api.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.geo.District;
import com.suwasarana.api.matching.MatchStatus;
import com.suwasarana.api.matching.MatchingEngineRepository;
import com.suwasarana.api.matching.RequestMatch;
import com.suwasarana.api.matching.RequestMatchRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.security.UserDetailsImpl;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Shared set-up for the lifecycle integration tests: the real application on H2 with the real controllers, security
 * and services. PostgreSQL-only SQL (earthdistance) is replaced by a mock of {@link MatchingEngineRepository}; the real
 * query is exercised against PostgreSQL in MatchingPostgresTest.
 *
 * Every test starts and ends with an empty database, so classes can share the H2 instance without disturbing each other.
 */
// H2 dialect: the PostgreSQL dialect emits "for no key update" for pessimistic locks, which H2 cannot parse
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@ActiveProfiles("test")
public abstract class LifecycleIntegrationBase {

    protected static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @Autowired protected WebApplicationContext context;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected DonorRepository donorRepository;
    @Autowired protected RequestRepository requestRepository;
    @Autowired protected RequestMatchRepository matchRepository;
    @Autowired protected AppClock clock;

    /** H2 has no earthdistance functions, so the candidate query is stubbed per test. */
    @MockitoBean protected MatchingEngineRepository matchingEngineRepository;

    protected MockMvc mvc;

    @BeforeEach
    void baseSetUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.setClock(null);
        cleanDatabase();
    }

    @AfterEach
    void baseTearDown() {
        clock.setClock(null);
        cleanDatabase();
    }

    protected void cleanDatabase() {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute("SET REFERENTIAL_INTEGRITY FALSE");
                List<String> tables = jdbc.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE UPPER(table_schema) = 'PUBLIC' AND table_type = 'BASE TABLE'",
                        String.class);
                for (String t : tables) st.execute("DELETE FROM \"" + t + "\"");
                st.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ fixtures

    protected User user(String name, Role role, VerificationStatus status) {
        int n = SEQ.incrementAndGet();
        User u = new User();
        u.setEmail(name + n + "@test.example");
        u.setPhoneNumber("077" + String.format("%07d", n));
        u.setFirstName(name);
        u.setLastName("Tester");
        u.setRole(role);
        u.setVerificationStatus(status);
        u.setEmailVerifiedAt(java.time.Instant.now()); // institutions need a confirmed email (VerificationGuard)
        return userRepository.save(u);
    }

    protected DonorProfile donor(User u, String bloodType, String district) {
        District.Entry c = District.get(district);
        DonorProfile p = new DonorProfile();
        p.setUser(u);
        p.setBloodType(bloodType);
        p.setDateOfBirth(LocalDate.now().minusYears(30));
        p.setWeightKg(new BigDecimal("70.00"));
        p.setDistrict(district);
        p.setLatitude(c.latitude());
        p.setLongitude(c.longitude());
        p.setTotalDonations(2);
        p.setLivesHelpedEstimate(6);
        return donorRepository.save(p);
    }

    protected BloodRequest request(User owner, RequestStatus status, String bloodType, int units, String district) {
        District.Entry c = District.get(district);
        LocalDateTime now = clock.utcNow();
        BloodRequest r = new BloodRequest();
        r.setRequester(owner);
        r.setPatientBloodType(bloodType);
        r.setUnitsNeeded((short) units);
        r.setUrgency(Urgency.URGENT);
        r.setHospitalName("Base Hospital " + district);
        r.setDistrict(district);
        r.setLatitude(c.latitude());
        r.setLongitude(c.longitude());
        r.setStatus(status);
        r.setCreatedAt(now.minusHours(1));
        r.setLastEscalatedAt(now.minusMinutes(30));
        r.setExpiresAt(now.plusHours(5));
        if (!status.isActive()) r.setClosedAt(now.minusMinutes(10));
        return requestRepository.save(r);
    }

    protected RequestMatch match(BloodRequest r, DonorProfile d, MatchStatus status) {
        return matchNotifiedMinutesAgo(r, d, status, 20);
    }

    /** notified_at cannot be changed after insert, so the age is given when the row is created. */
    protected RequestMatch matchNotifiedMinutesAgo(BloodRequest r, DonorProfile d, MatchStatus status, int minutesAgo) {
        RequestMatch m = new RequestMatch();
        m.setRequest(r);
        m.setDonor(d);
        m.setStatus(status);
        m.setDistanceKm(new BigDecimal("2.50"));
        m.setCreatedAt(clock.utcNow().minusMinutes(minutesAgo));
        return matchRepository.save(m);
    }

    /** Stubs the candidate query to return these donors, nearest first (distance 1 km, 2 km, ...). */
    protected void stubCandidates(DonorProfile... donors) {
        List<Object[]> rows = new java.util.ArrayList<>();
        for (int i = 0; i < donors.length; i++) rows.add(new Object[]{donors[i].getId(), 1.0 + i, new BigDecimal("50.00")});
        org.mockito.Mockito.when(matchingEngineRepository.findCandidateRows(
                org.mockito.ArgumentMatchers.anyCollection(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(rows);
    }

    // ------------------------------------------------------------------ HTTP helpers

    /** Authenticates the request as this user (the same principal type the JWT filter produces). */
    protected RequestPostProcessor as(User u) {
        UserDetailsImpl principal = UserDetailsImpl.build(u);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /**
     * The rate limiter allows only a few request creations per minute per client address. Tests act as many different
     * clients, so each call comes from its own address.
     */
    protected RequestPostProcessor fromNewClient() {
        int n = SEQ.incrementAndGet();
        return req -> {
            req.setRemoteAddr("10." + (n / 65536 % 256) + "." + (n / 256 % 256) + "." + (n % 256));
            return req;
        };
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode data(MvcResult result) throws Exception {
        return json(result).get("data");
    }
}
