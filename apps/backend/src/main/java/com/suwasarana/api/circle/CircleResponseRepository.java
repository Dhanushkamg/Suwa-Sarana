package com.suwasarana.api.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CircleResponseRepository extends JpaRepository<CircleResponse, Long> {
    List<CircleResponse> findByCircleIdOrderByRespondedAtDesc(Long circleId);
}
