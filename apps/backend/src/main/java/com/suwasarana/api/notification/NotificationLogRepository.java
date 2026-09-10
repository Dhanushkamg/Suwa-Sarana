package com.suwasarana.api.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByDonorId(Long donorId);
    List<NotificationLog> findByRequestId(Long requestId);
}
