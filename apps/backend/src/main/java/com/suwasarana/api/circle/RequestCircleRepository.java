package com.suwasarana.api.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RequestCircleRepository extends JpaRepository<RequestCircle, Long> {
    Optional<RequestCircle> findByInviteToken(String inviteToken);
    Optional<RequestCircle> findByRequestId(Long requestId);
}
