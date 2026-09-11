package com.suwasarana.api.camp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DonationCampRepository extends JpaRepository<DonationCamp, Long> {
    List<DonationCamp> findByStatusAndScheduledDateGreaterThanEqualOrderByScheduledDateAsc(String status, LocalDate date);
    List<DonationCamp> findByOrganizerId(Long organizerId);
}
