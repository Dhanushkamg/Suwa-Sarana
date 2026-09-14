package com.suwasarana.api.slot;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DonationSlotRepository extends JpaRepository<DonationSlot, Long> {

    /**
     * Acquires a PESSIMISTIC_WRITE lock on the slot row before reading it.
     * Used in SlotBookingService.bookSlot() to prevent concurrent over-booking
     * of the same slot's bookedCount / capacity fields.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DonationSlot s WHERE s.id = :id")
    Optional<DonationSlot> findByIdForUpdate(@Param("id") Long id);

    List<DonationSlot> findByCampIdAndStatusOrderByStartTimeAsc(Long campId, SlotStatus status);

    List<DonationSlot> findByCampIdOrderByStartTimeAsc(Long campId);

    List<DonationSlot> findByHospitalUserIdAndStatusOrderByStartTimeAsc(Long hospitalUserId, SlotStatus status);

    List<DonationSlot> findByHospitalUserIdOrderByStartTimeAsc(Long hospitalUserId);

    /**
     * Used by SlotReminderScheduler to find slots whose start time falls within
     * a given window (e.g. 23h–25h from now for the 24h reminder job).
     */
    @Query("SELECT s FROM DonationSlot s WHERE s.startTime BETWEEN :from AND :to AND s.status = 'OPEN'")
    List<DonationSlot> findSlotsStartingBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
