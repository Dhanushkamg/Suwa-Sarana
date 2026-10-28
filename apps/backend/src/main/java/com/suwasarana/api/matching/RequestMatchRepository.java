package com.suwasarana.api.matching;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface RequestMatchRepository extends JpaRepository<RequestMatch, Long> {
    List<RequestMatch> findByRequestId(Long requestId);

    List<RequestMatch> findByDonorId(Long donorId);

    /** Donor's own matches with the request in the same query (no N+1). */
    @EntityGraph(attributePaths = "request")
    List<RequestMatch> findByDonorIdOrderByCreatedAtDesc(Long donorId);

    /** A request's matches with donor and donor's user loaded, nearest first (requester view). */
    @EntityGraph(attributePaths = {"donor", "donor.user"})
    List<RequestMatch> findByRequestIdOrderByDistanceKmAscIdAsc(Long requestId);

    @EntityGraph(attributePaths = {"donor", "donor.user", "request", "request.requester"})
    List<RequestMatch> findByRequestIdAndStatusIn(Long requestId, Collection<MatchStatus> statuses);

    @EntityGraph(attributePaths = {"donor", "donor.user", "request"})
    List<RequestMatch> findByIdIn(Collection<Long> ids);

    boolean existsByRequestIdAndDonorId(Long requestId, Long donorId);

    boolean existsByRequestIdAndDonorUserId(Long requestId, Long userId);

    long countByRequestAndStatus(com.suwasarana.api.request.BloodRequest request, MatchStatus status);

    long countByRequestIdAndStatus(Long requestId, MatchStatus status);

    /** Accepted matches whose outcome has not been recorded as a no-show (donated is null or true). */
    @Query("SELECT COUNT(m) FROM RequestMatch m WHERE m.request.id = :requestId AND m.status = com.suwasarana.api.matching.MatchStatus.ACCEPTED "
            + "AND (m.donated IS NULL OR m.donated = true)")
    long countLiveAccepted(@Param("requestId") Long requestId);

    /** Accepted matches not yet confirmed either way. */
    @Query("SELECT m FROM RequestMatch m WHERE m.request.id = :requestId AND m.status = com.suwasarana.api.matching.MatchStatus.ACCEPTED AND m.donated IS NULL")
    List<RequestMatch> findUnconfirmedAccepted(@Param("requestId") Long requestId);

    /** Pending matches notified before the cutoff (response timeout job). */
    List<RequestMatch> findByStatusAndCreatedAtBefore(MatchStatus status, LocalDateTime cutoff);
}
