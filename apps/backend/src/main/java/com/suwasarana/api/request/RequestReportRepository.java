package com.suwasarana.api.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestReportRepository extends JpaRepository<RequestReport, Long> {
    List<RequestReport> findByRequestId(Long requestId);

    boolean existsByRequestIdAndReportedById(Long requestId, Long reporterId);

    long countByRequestId(Long requestId);
}
