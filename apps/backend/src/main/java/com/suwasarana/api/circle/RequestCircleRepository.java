package com.suwasarana.api.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RequestCircleRepository extends JpaRepository<RequestCircle, Long> {

    Optional<RequestCircle> findByInviteTokenHash(String inviteTokenHash);

    /**
     * Serialises concurrent pledges to one circle so the per-circle and per-phone caps are exact.
     * Plain {@code FOR UPDATE} (native) because it runs unchanged on PostgreSQL and on the H2 used by the tests;
     * Hibernate's generated {@code FOR NO KEY UPDATE} is not understood by H2.
     */
    @Query(value = "SELECT * FROM request_circles WHERE invite_token_hash = :hash FOR UPDATE", nativeQuery = true)
    Optional<RequestCircle> findByInviteTokenHashForUpdate(@Param("hash") String hash);

    Optional<RequestCircle> findByRequestId(Long requestId);
}
