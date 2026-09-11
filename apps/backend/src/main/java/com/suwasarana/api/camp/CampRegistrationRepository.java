package com.suwasarana.api.camp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampRegistrationRepository extends JpaRepository<CampRegistration, Long> {
    boolean existsByCampIdAndDonorId(Long campId, Long donorId);
    Optional<CampRegistration> findByCampIdAndDonorId(Long campId, Long donorId);
}
