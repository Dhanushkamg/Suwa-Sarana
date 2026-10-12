package com.suwasarana.api.camp;

import org.springframework.data.jpa.repository.EntityGraph;
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
    List<DonationCamp> findByOrganizerId(Long organizerId);
    @EntityGraph(attributePaths = "organizer")
    List<DonationCamp> findByOrganizerIdOrderByScheduledDateDesc(Long organizerId);
}
