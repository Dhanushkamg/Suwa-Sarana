package com.suwasarana.api.camp;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DonationCampRepository extends JpaRepository<DonationCamp, Long> {
    List<DonationCamp> findByStatusAndScheduledDateGreaterThanEqualOrderByScheduledDateAsc(String status, LocalDate date);
    // Organizer is fetched eagerly: the DTO shows the organizer's name for every camp.
    @EntityGraph(attributePaths = "organizer")
    List<DonationCamp> findByStatusOrderByScheduledDateAsc(String status);
    /** Camps of the given status on or after {@code from}, soonest first, organiser loaded for the DTO. */
    @EntityGraph(attributePaths = "organizer")
    @Query("SELECT c FROM DonationCamp c WHERE c.status = :status AND c.scheduledDate >= :from ORDER BY c.scheduledDate ASC, c.startTime ASC")
    List<DonationCamp> findUpcoming(@Param("status") String status, @Param("from") LocalDate from);

    List<DonationCamp> findByOrganizerId(Long organizerId);
    @EntityGraph(attributePaths = "organizer")
    List<DonationCamp> findByOrganizerIdOrderByScheduledDateDesc(Long organizerId);
}
