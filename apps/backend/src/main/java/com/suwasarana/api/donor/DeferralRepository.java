package com.suwasarana.api.donor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DeferralRepository extends JpaRepository<DonorDeferral, Long> {

    @Query("SELECT d FROM DonorDeferral d WHERE d.donor.id = :donorId AND d.deferredUntil >= :today")
    List<DonorDeferral> findActiveDeferrals(@Param("donorId") Long donorId, @Param("today") LocalDate today);
}
