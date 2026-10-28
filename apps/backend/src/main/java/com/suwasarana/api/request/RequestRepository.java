package com.suwasarana.api.request;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RequestRepository extends JpaRepository<BloodRequest, Long> {

    /** OPEN and ESCALATING only (IVR and escalation both read this; MATCHED is handled by {@link #findLiveRequests()}). */
    @Query("SELECT r FROM BloodRequest r WHERE r.status IN ('OPEN', 'ESCALATING')")
    List<BloodRequest> findActiveRequests();

    /** OPEN, ESCALATING and MATCHED: everything the escalation/lifecycle job has to look at. */
    @Query("SELECT r FROM BloodRequest r WHERE r.status IN ('OPEN', 'ESCALATING', 'MATCHED')")
    List<BloodRequest> findLiveRequests();

    List<BloodRequest> findAllByOrderByCreatedAtDesc();

    List<BloodRequest> findAllByOrderByFraudRiskScoreDesc();

    List<BloodRequest> findByRequesterId(Long requesterId);

    List<BloodRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    @Modifying
    @Query("UPDATE BloodRequest r SET r.currentRadiusKm = :radius WHERE r.id = :id")
    void updateRadius(@Param("id") Long id, @Param("radius") short radius);

    @Modifying
    @Query("UPDATE BloodRequest r SET r.status = :status WHERE r.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") RequestStatus status);

    List<BloodRequest> findByStatusInOrderByCreatedAtDesc(Collection<RequestStatus> statuses);

    /** Requests donors may browse: still needing donors and not past their expiry. */
    List<BloodRequest> findByStatusInAndExpiresAtAfterOrderByCreatedAtDesc(Collection<RequestStatus> statuses, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM BloodRequest r WHERE r.expiresAt < :cutoffDate AND r.status IN :statuses")
    int deleteByStatusInAndExpiresAtBefore(@Param("statuses") Collection<RequestStatus> statuses,
                                           @Param("cutoffDate") LocalDateTime cutoffDate);

    // ---- abuse limits
    long countByRequesterIdAndStatusIn(Long requesterId, Collection<RequestStatus> statuses);

    @Query("SELECT r FROM BloodRequest r WHERE r.requester.id = :requesterId AND r.patientBloodType = :bloodType "
            + "AND LOWER(r.hospitalName) = LOWER(:hospitalName) AND r.status IN :statuses ORDER BY r.createdAt DESC")
    List<BloodRequest> findDuplicates(@Param("requesterId") Long requesterId,
                                      @Param("bloodType") String bloodType,
                                      @Param("hospitalName") String hospitalName,
                                      @Param("statuses") Collection<RequestStatus> statuses);

    // ---- keyset paging for streaming CSV export
    List<BloodRequest> findByIdGreaterThanOrderByIdAsc(Long afterId, Pageable pageable);

    List<BloodRequest> findByRequesterIdAndIdGreaterThanOrderByIdAsc(Long requesterId, Long afterId, Pageable pageable);

    @Query("SELECT r FROM BloodRequest r WHERE r.id > :afterId AND r.id IN "
            + "(SELECT m.request.id FROM RequestMatch m WHERE m.donor.user.id = :userId) ORDER BY r.id ASC")
    List<BloodRequest> findMatchedToUser(@Param("userId") Long userId, @Param("afterId") Long afterId, Pageable pageable);

    Optional<BloodRequest> findFirstByRequesterIdOrderByCreatedAtDesc(Long requesterId);
}
