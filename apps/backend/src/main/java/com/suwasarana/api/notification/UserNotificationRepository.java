package com.suwasarana.api.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    // Keyset pagination: the cursor is the id of the last row of the previous page.
    List<UserNotification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<UserNotification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Pageable pageable);

    List<UserNotification> findByUserIdAndReadAtIsNullOrderByIdDesc(Long userId, Pageable pageable);

    List<UserNotification> findByUserIdAndReadAtIsNullAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Pageable pageable);

    /** Replay after a reconnect: everything newer than Last-Event-ID, oldest first. */
    List<UserNotification> findByUserIdAndIdGreaterThanOrderByIdAsc(Long userId, Long afterId, Pageable pageable);

    Optional<UserNotification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
