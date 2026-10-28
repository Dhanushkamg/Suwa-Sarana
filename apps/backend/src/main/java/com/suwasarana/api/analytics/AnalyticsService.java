package com.suwasarana.api.analytics;

import com.suwasarana.api.analytics.dto.DistrictSummaryDto;
import com.suwasarana.api.analytics.dto.NationalSummaryDto;
import com.suwasarana.api.donor.DeferralRulesConfig;
import com.suwasarana.api.geo.District;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.user.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One implementation of every dashboard number, used by the admin overview, the heatmap and the national KPIs.
 * Everything is a SQL GROUP BY (never findAll()), districts are normalised to the 25 official names, and national
 * figures come from their own database-wide totals.
 *
 * <h3>Definitions</h3>
 * <ul>
 *   <li><b>activeRequests</b>: status OPEN, ESCALATING or MATCHED and expires_at in the future.
 *       <b>awaitingRequests</b>: the MATCHED subset.</li>
 *   <li><b>closed in window</b>: status FULFILLED, EXPIRED or CANCELLED with closed_at in the last
 *       {@code windowDays} days (default 30).</li>
 *   <li><b>fulfillmentRate</b> = fulfilled / (fulfilled + expired + cancelled) over the window, percent with one
 *       decimal; {@code null} when the denominator is 0 (the UI shows "no data", never 0 % or 100 %).</li>
 *   <li><b>registeredDonors</b> (national) = users with role DONOR; per district = donor profiles in that district;
 *       <b>donorsWithProfile</b> = rows in donor_profiles.</li>
 *   <li><b>availableDonors</b> = profile is_available, age within the NBTS limits on today's date (first-time donors up
 *       to 55), weight unknown or above 50 kg, and no active deferral. The numbers come from
 *       {@link DeferralRulesConfig}, the same source {@code DeferralService} uses.</li>
 *   <li><b>unitsOutstanding</b> = sum over active requests of max(0, unitsNeeded - live accepted donors).</li>
 *   <li><b>districtsWithData</b> = districts with at least one registered donor, active request or request closed in the window.</li>
 * </ul>
 * Shortage thresholds are configuration, not facts: the owner/NBTS should set
 * {@code app.analytics.shortage.*}; the defaults reproduce the previous behaviour.
 */
@Service
public class AnalyticsService {

    private static final Set<RequestStatus> ACTIVE = EnumSet.of(RequestStatus.OPEN, RequestStatus.ESCALATING, RequestStatus.MATCHED);

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private AppClock clock;

    @Value("${app.analytics.window-days:30}")
    private int windowDays = 30;

    /** available donors per outstanding unit below which a district is CRITICAL. */
    @Value("${app.analytics.shortage.critical-ratio:2.0}")
    private double criticalRatio = 2.0;

    /** ... and below which it is a WARNING. */
    @Value("${app.analytics.shortage.warning-ratio:5.0}")
    private double warningRatio = 5.0;

    /** Available donors from which a district with no open need counts as SURPLUS. */
    @Value("${app.analytics.shortage.surplus-donors:10}")
    private long surplusDonors = 10;

    /** Raw per-district counters before the 25-name normalisation. */
    private static final class Counts {
        long active, awaiting, fulfilled, expired, cancelled, total;
        long registered, available, unitsOutstanding;

        void add(Counts o) {
            active += o.active; awaiting += o.awaiting; fulfilled += o.fulfilled; expired += o.expired;
            cancelled += o.cancelled; total += o.total; registered += o.registered; available += o.available;
            unitsOutstanding += o.unitsOutstanding;
        }

        long closed() { return fulfilled + expired + cancelled; }
    }

    // ------------------------------------------------------------------ public API

    /** All 25 districts, in the fixed official order, each present exactly once. */
    @Transactional(readOnly = true)
    public List<DistrictSummaryDto> districtSummaries(boolean admin) {
        Map<String, Counts> byDistrict = loadDistrictCounts().perDistrict();
        List<DistrictSummaryDto> out = new ArrayList<>();
        for (District.Entry d : District.all()) {
            out.add(toDistrictDto(d, byDistrict.getOrDefault(d.name(), new Counts()), admin));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public NationalSummaryDto nationalSummary(boolean admin) {
        LocalDateTime now = clock.now();
        LocalDateTime from = now.minusDays(windowDays);
        LoadedCounts loaded = loadDistrictCounts();

        // National figures come from their own totals over ALL requests, not from the district rows.
        Object[] t = (Object[]) em.createQuery(
                        "SELECT "
                                + "SUM(CASE WHEN r.status IN :active AND r.expiresAt > :now THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status = :matched AND r.expiresAt > :now THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status = :fulfilled AND r.closedAt >= :from THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status = :expired AND r.closedAt >= :from THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status = :cancelled AND r.closedAt >= :from THEN 1 ELSE 0 END) "
                                + "FROM BloodRequest r")
                .setParameter("active", ACTIVE).setParameter("now", now).setParameter("matched", RequestStatus.MATCHED)
                .setParameter("fulfilled", RequestStatus.FULFILLED).setParameter("expired", RequestStatus.EXPIRED)
                .setParameter("cancelled", RequestStatus.CANCELLED).setParameter("from", from)
                .getSingleResult();
        long active = num(t[0]), awaiting = num(t[1]), fulfilled = num(t[2]), expired = num(t[3]), cancelled = num(t[4]);

        long registeredDonors = ((Number) em.createQuery("SELECT COUNT(u) FROM User u WHERE u.role = :role")
                .setParameter("role", Role.DONOR).getSingleResult()).longValue();
        long withProfile = ((Number) em.createQuery("SELECT COUNT(d) FROM DonorProfile d").getSingleResult()).longValue();
        long available = loaded.nationalAvailable();

        NationalSummaryDto dto = new NationalSummaryDto();
        dto.setWindowDays(windowDays);
        dto.setGeneratedAt(clock.instant());
        dto.setActiveRequests(active);
        dto.setAwaitingRequests(awaiting);
        dto.setFulfilledRequests(fulfilled);
        dto.setClosedRequests(fulfilled + expired + cancelled);
        dto.setFulfillmentRate(rate(fulfilled, fulfilled + expired + cancelled));
        dto.setRegisteredDonors(registeredDonors);
        dto.setDonorsWithProfile(withProfile);
        dto.setAvailableDonors(available);
        dto.setDistrictsTotal(District.all().size());
        int withData = 0;
        for (District.Entry d : District.all()) {
            Counts c = loaded.perDistrict().get(d.name());
            if (c != null && (c.registered > 0 || c.active > 0 || c.closed() > 0)) withData++;
        }
        dto.setDistrictsWithData(withData);
        if (admin) {
            dto.setExpiredRequests(expired);
            dto.setCancelledRequests(cancelled);
            dto.setUnitsOutstanding(loaded.nationalUnitsOutstanding());
            dto.setUnmappedRequests(loaded.unmappedRequests());
        }
        return dto;
    }

    /** Admin overview bars: active requests per district (only districts that have any), same definition as the heatmap. */
    @Transactional(readOnly = true)
    public Map<String, Long> activeRequestsByDistrict() {
        LoadedCounts loaded = loadDistrictCounts();
        Map<String, Long> out = new LinkedHashMap<>();
        for (District.Entry d : District.all()) {
            Counts c = loaded.perDistrict().get(d.name());
            if (c != null && c.active > 0) out.put(d.name(), c.active);
        }
        if (loaded.unmappedActive() > 0) out.put("Unmapped", loaded.unmappedActive());
        return out;
    }

    // ------------------------------------------------------------------ loading

    private record LoadedCounts(Map<String, Counts> perDistrict, Counts unmapped) {
        long nationalAvailable() {
            return perDistrict.values().stream().mapToLong(c -> c.available).sum() + unmapped.available;
        }
        long nationalUnitsOutstanding() {
            return perDistrict.values().stream().mapToLong(c -> c.unitsOutstanding).sum() + unmapped.unitsOutstanding;
        }
        long unmappedRequests() { return unmapped.total; }
        long unmappedActive() { return unmapped.active; }
    }

    private LoadedCounts loadDistrictCounts() {
        LocalDateTime now = clock.now();
        LocalDateTime from = now.minusDays(windowDays);
        LocalDate today = clock.today();
        Map<String, Counts> perDistrict = new HashMap<>();
        Counts unmapped = new Counts();

        // 1. request counters by raw district text, one GROUP BY
        @SuppressWarnings("unchecked")
        List<Object[]> reqRows = em.createQuery(
                        "SELECT r.district, "
                                + "SUM(CASE WHEN r.status IN :active AND r.expiresAt > :now THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status = :matched AND r.expiresAt > :now THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status = :fulfilled AND r.closedAt >= :from THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status = :expired AND r.closedAt >= :from THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status = :cancelled AND r.closedAt >= :from THEN 1 ELSE 0 END), "
                                + "COUNT(r) "
                                + "FROM BloodRequest r GROUP BY r.district")
                .setParameter("active", ACTIVE).setParameter("now", now).setParameter("matched", RequestStatus.MATCHED)
                .setParameter("fulfilled", RequestStatus.FULFILLED).setParameter("expired", RequestStatus.EXPIRED)
                .setParameter("cancelled", RequestStatus.CANCELLED).setParameter("from", from)
                .getResultList();
        for (Object[] row : reqRows) {
            Counts c = new Counts();
            c.active = num(row[1]); c.awaiting = num(row[2]); c.fulfilled = num(row[3]);
            c.expired = num(row[4]); c.cancelled = num(row[5]); c.total = num(row[6]);
            bucket(perDistrict, unmapped, (String) row[0]).add(c);
        }

        // 2. donors per raw district text
        @SuppressWarnings("unchecked")
        List<Object[]> donorRows = em.createQuery("SELECT d.district, COUNT(d) FROM DonorProfile d GROUP BY d.district").getResultList();
        for (Object[] row : donorRows) {
            Counts c = new Counts();
            c.registered = num(row[1]);
            bucket(perDistrict, unmapped, (String) row[0]).add(c);
        }

        // 3. available donors: same rules as DeferralService, from DeferralRulesConfig
        @SuppressWarnings("unchecked")
        List<Object[]> availRows = em.createQuery(
                        "SELECT d.district, COUNT(d) FROM DonorProfile d "
                                + "WHERE d.isAvailable = true "
                                + "AND d.dateOfBirth <= :oldEnough AND d.dateOfBirth > :notTooOld "
                                + "AND (d.totalDonations > 0 OR d.dateOfBirth > :notTooOldFirstTime) "
                                + "AND (d.weightKg IS NULL OR d.weightKg > :minWeight) "
                                + "AND NOT EXISTS (SELECT f.id FROM DonorDeferral f WHERE f.donor.id = d.id AND f.deferredUntil >= :today) "
                                + "GROUP BY d.district")
                .setParameter("oldEnough", today.minusYears(DeferralRulesConfig.MIN_DONOR_AGE))
                .setParameter("notTooOld", today.minusYears(DeferralRulesConfig.MAX_DONOR_AGE + 1))
                .setParameter("notTooOldFirstTime", today.minusYears(DeferralRulesConfig.MAX_FIRST_TIME_DONOR_AGE + 1))
                .setParameter("minWeight", DeferralRulesConfig.MIN_DONOR_WEIGHT_KG)
                .setParameter("today", today)
                .getResultList();
        for (Object[] row : availRows) {
            Counts c = new Counts();
            c.available = num(row[1]);
            bucket(perDistrict, unmapped, (String) row[0]).add(c);
        }

        // 4. units outstanding: active requests minus their live accepted donors
        @SuppressWarnings("unchecked")
        List<Object[]> activeReqs = em.createQuery(
                        "SELECT r.id, r.district, r.unitsNeeded FROM BloodRequest r WHERE r.status IN :active AND r.expiresAt > :now")
                .setParameter("active", ACTIVE).setParameter("now", now).getResultList();
        Map<Long, Long> acceptedByRequest = new HashMap<>();
        if (!activeReqs.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Object[]> acc = em.createQuery(
                            "SELECT m.request.id, COUNT(m) FROM RequestMatch m "
                                    + "WHERE m.status = com.suwasarana.api.matching.MatchStatus.ACCEPTED "
                                    + "AND (m.donated IS NULL OR m.donated = true) "
                                    + "AND m.request.status IN :active AND m.request.expiresAt > :now "
                                    + "GROUP BY m.request.id")
                    .setParameter("active", ACTIVE).setParameter("now", now).getResultList();
            for (Object[] row : acc) acceptedByRequest.put(((Number) row[0]).longValue(), num(row[1]));
        }
        for (Object[] row : activeReqs) {
            long accepted = acceptedByRequest.getOrDefault(((Number) row[0]).longValue(), 0L);
            Counts c = new Counts();
            c.unitsOutstanding = Math.max(0, num(row[2]) - accepted);
            bucket(perDistrict, unmapped, (String) row[1]).add(c);
        }
        return new LoadedCounts(perDistrict, unmapped);
    }

    /** Folds free-text district names ("colombo", "Colombo District") into the official 25; unknown ones go to "unmapped". */
    private static Counts bucket(Map<String, Counts> perDistrict, Counts unmapped, String rawDistrict) {
        return District.normalise(rawDistrict)
                .map(name -> perDistrict.computeIfAbsent(name, k -> new Counts()))
                .orElse(unmapped);
    }

    // ------------------------------------------------------------------ mapping and rules

    private DistrictSummaryDto toDistrictDto(District.Entry d, Counts c, boolean admin) {
        DistrictSummaryDto dto = new DistrictSummaryDto();
        dto.setDistrict(d.name());
        dto.setLatitude(d.latitude());
        dto.setLongitude(d.longitude());
        dto.setDonorCount(c.registered);
        dto.setRegisteredDonors(c.registered);
        dto.setAvailableDonors(c.available);
        dto.setActiveRequests(c.active);
        dto.setAwaitingRequests(c.awaiting);
        dto.setFulfilledRequests(c.fulfilled);
        dto.setClosedRequests(c.closed());
        dto.setTotalRequests(c.total);
        dto.setFulfillmentRate(rate(c.fulfilled, c.closed()));
        dto.setHasData(c.registered > 0 || c.active > 0 || c.closed() > 0);
        dto.setWindowDays(windowDays);
        dto.setShortageLevel(shortageLevel(c));
        if (admin) {
            dto.setExpiredRequests(c.expired);
            dto.setCancelledRequests(c.cancelled);
            dto.setUnitsOutstanding(c.unitsOutstanding);
        }
        return dto;
    }

    /** Percentage with one decimal, or null when there is nothing to divide by. */
    static Double rate(long fulfilled, long closed) {
        if (closed <= 0) return null;
        return BigDecimal.valueOf(fulfilled * 100.0 / closed).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String shortageLevel(Counts c) {
        if (c.registered == 0 && c.active == 0) return "NO_DATA";
        if (c.unitsOutstanding > 0) {
            double ratio = (double) c.available / c.unitsOutstanding;
            if (ratio < criticalRatio) return "CRITICAL";
            if (ratio < warningRatio) return "WARNING";
            return "BALANCED";
        }
        if (c.active > 0) return "BALANCED"; // everything needed is already covered by accepted donors
        return c.available >= surplusDonors ? "SURPLUS" : "BALANCED";
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
