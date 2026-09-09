package com.suwasarana.api.matching;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestMatchRepository extends JpaRepository<RequestMatch, Long> {
    List<RequestMatch> findByRequestId(Long requestId);
    List<RequestMatch> findByDonorId(Long donorId);
    boolean existsByRequestIdAndDonorId(Long requestId, Long donorId);
    long countByRequestAndStatus(com.suwasarana.api.request.BloodRequest request, MatchStatus status);
}
