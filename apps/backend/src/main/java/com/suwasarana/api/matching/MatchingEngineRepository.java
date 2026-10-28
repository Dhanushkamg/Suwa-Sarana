package com.suwasarana.api.matching;

import com.suwasarana.api.donor.DonorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface MatchingEngineRepository extends JpaRepository<DonorProfile, Long> {

    /**
     * Candidate donors for a request, as rows of {@code [donor_id, distance_km, reliability_score]}.
     *
     * <p>Index use: {@code earth_box(centre, radius) @> ll_to_earth(lat, lon)} is the form the GiST index
     * {@code idx_donor_geo} (V1) can answer, so only donors inside the bounding box are visited. The exact
     * {@code earth_distance} test then trims the box corners to a true circle. (A bare {@code earth_distance(...) <= r}
     * cannot use the index and scans every donor.)
     *
     * <p>Ordering: when {@code critical} is true the nearest donor comes first (time matters most), reliability only
     * breaks ties. Otherwise donors are ranked by {@code 0.6 * reliability + 0.4 * proximity (0-100 within the radius)}
     * plus 10 points for an exact blood-type match (keeps O- donors for O- patients when others are available).
     */
    @Query(value = """
            SELECT d.id AS donor_id,
                   earth_distance(ll_to_earth(CAST(:lat AS double precision), CAST(:lon AS double precision)),
                                  ll_to_earth(d.latitude, d.longitude)) / 1000.0 AS distance_km,
                   d.reliability_score AS reliability
            FROM donor_profiles d
            WHERE d.blood_type IN (:types)
              AND d.is_available = true
              AND earth_box(ll_to_earth(CAST(:lat AS double precision), CAST(:lon AS double precision)),
                            CAST(:radiusM AS double precision)) @> ll_to_earth(d.latitude, d.longitude)
              AND earth_distance(ll_to_earth(CAST(:lat AS double precision), CAST(:lon AS double precision)),
                                 ll_to_earth(d.latitude, d.longitude)) <= CAST(:radiusM AS double precision)
              AND NOT EXISTS (
                    SELECT 1 FROM request_matches rm WHERE rm.donor_id = d.id AND rm.request_id = :requestId)
              AND NOT EXISTS (
                    SELECT 1 FROM donor_deferrals df WHERE df.donor_id = d.id AND df.deferred_until >= :today)
            ORDER BY
              CASE WHEN CAST(:critical AS boolean)
                   THEN earth_distance(ll_to_earth(CAST(:lat AS double precision), CAST(:lon AS double precision)),
                                       ll_to_earth(d.latitude, d.longitude)) END ASC NULLS LAST,
              ( 0.6 * d.reliability_score
                + 0.4 * GREATEST(0, 100.0 * (1.0 - earth_distance(ll_to_earth(CAST(:lat AS double precision), CAST(:lon AS double precision)),
                                                                   ll_to_earth(d.latitude, d.longitude)) / CAST(:radiusM AS double precision)))
                + CASE WHEN d.blood_type = :exactType THEN 10 ELSE 0 END ) DESC,
              earth_distance(ll_to_earth(CAST(:lat AS double precision), CAST(:lon AS double precision)),
                             ll_to_earth(d.latitude, d.longitude)) ASC,
              d.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findCandidateRows(
            @Param("types") Collection<String> types,
            @Param("exactType") String exactType,
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusM") double radiusMetres,
            @Param("requestId") Long requestId,
            @Param("today") LocalDate today,
            @Param("critical") boolean critical,
            @Param("limit") int limit
    );
}
