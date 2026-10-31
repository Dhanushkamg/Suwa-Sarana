package com.suwasarana.api.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiTriageRecordRepository extends JpaRepository<AiTriageRecord, Long> {

    Optional<AiTriageRecord> findFirstByRequestIdOrderByCreatedAtDescIdDesc(Long requestId);

    List<AiTriageRecord> findByRequestIdOrderByCreatedAtDescIdDesc(Long requestId);
}
