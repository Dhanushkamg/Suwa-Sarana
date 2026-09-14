package com.suwasarana.api.slot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlotBookingRepository extends JpaRepository<SlotBooking, Long> {

    Optional<SlotBooking> findBySlotIdAndDonorId(Long slotId, Long donorId);

    List<SlotBooking> findByDonorIdOrderByBookedAtDesc(Long donorId);

    List<SlotBooking> findBySlotIdAndStatus(Long slotId, BookingStatus status);

    List<SlotBooking> findBySlotId(Long slotId);
}
