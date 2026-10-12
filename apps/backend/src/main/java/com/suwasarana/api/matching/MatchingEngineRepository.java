package com.suwasarana.api.matching;

import com.suwasarana.api.donor.DonorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchingEngineRepository extends JpaRepository<DonorProfile, Long> {

    @Query(value = """
            SELECT d.* FROM donor_profiles d
            WHERE d.blood_type = :bloodType
            AND d.is_available = true
            AND (earth_distance(ll_to_earth(:lat, :lon), ll_to_earth(d.latitude, d.longitude)) / 1000) <= :radiusKm
            AND NOT EXISTS (
                SELECT 1 FROM request_matches rm WHERE rm.donor_id = d.id AND rm.request_id = :requestId
            )
            AND NOT EXISTS (
                SELECT 1 FROM donor_deferrals df WHERE df.donor_id = d.id AND df.deferred_until >= CURRENT_DATE
            )
            ORDER BY d.reliability_score DESC,
                     earth_distance(ll_to_earth(:lat, :lon), ll_to_earth(d.latitude, d.longitude)) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DonorProfile> findEligibleDonorsWithinRadius(
            @Param("bloodType") String bloodType,
            @Param("lat") Double lat,
            @Param("lon") Double lon,
            @Param("radiusKm") short radiusKm,
            @Param("requestId") Long requestId,
            @Param("limit") int limit
    );
}
