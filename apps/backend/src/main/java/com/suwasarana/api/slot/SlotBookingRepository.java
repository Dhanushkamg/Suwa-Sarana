package com.suwasarana.api.slot;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SlotBookingRepository extends JpaRepository<SlotBooking, Long> {

    Optional<SlotBooking> findBySlotIdAndDonorId(Long slotId, Long donorId);

    // Fetch slot + host eagerly so mapping the DTOs does not trigger one query per booking.
    @EntityGraph(attributePaths = {"slot", "slot.camp", "slot.hospitalUser"})
    List<SlotBooking> findByDonorIdOrderByBookedAtDesc(Long donorId);

    List<SlotBooking> findBySlotIdAndStatus(Long slotId, BookingStatus status);

    List<SlotBooking> findBySlotId(Long slotId);

    /** Active (BOOKED) bookings of this donor whose slot overlaps [start, end), excluding the slot being booked. */
    @Query("SELECT COUNT(b) FROM SlotBooking b WHERE b.donor.id = :donorId AND b.status = com.suwasarana.api.slot.BookingStatus.BOOKED "
            + "AND b.slot.id <> :slotId AND b.slot.startTime < :end AND b.slot.endTime > :start")
    long countOverlappingBookings(@Param("donorId") Long donorId,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end,
                                  @Param("slotId") Long slotId);

    /** Organiser roster: bookings with donor and donor user loaded, in booking order. */
    @EntityGraph(attributePaths = {"donor", "donor.user"})
    @Query("SELECT b FROM SlotBooking b WHERE b.slot.id = :slotId ORDER BY b.bookedAt ASC, b.id ASC")
    List<SlotBooking> findRosterBySlotId(@Param("slotId") Long slotId);
}
