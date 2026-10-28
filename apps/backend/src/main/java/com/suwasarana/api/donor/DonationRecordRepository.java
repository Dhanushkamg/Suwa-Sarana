package com.suwasarana.api.donor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DonationRecordRepository extends JpaRepository<DonationRecord, Long> {

    Optional<DonationRecord> findByMatchId(Long matchId);

    Optional<DonationRecord> findByBookingId(Long bookingId);

    List<DonationRecord> findByDonorIdOrderByDonatedOnDescIdDesc(Long donorId, Pageable pageable);

    long countByRequestId(Long requestId);

    @Query("SELECT MAX(d.donatedOn) FROM DonationRecord d WHERE d.donorId = :donorId")
    Optional<LocalDate> findLastDonationDate(@Param("donorId") Long donorId);

    // ---- keyset paging for streaming CSV export (id ascending, never loads everything at once)
    List<DonationRecord> findByIdGreaterThanOrderByIdAsc(Long afterId, Pageable pageable);

    List<DonationRecord> findByDonorIdAndIdGreaterThanOrderByIdAsc(Long donorId, Long afterId, Pageable pageable);

    List<DonationRecord> findByHostUserIdAndIdGreaterThanOrderByIdAsc(Long hostUserId, Long afterId, Pageable pageable);
}
