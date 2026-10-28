package com.suwasarana.api.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByDonorId(Long donorId);
    List<NotificationLog> findByRequestId(Long requestId);

    /**
     * Delivery results are written from after-commit callbacks, where the original transaction is already finished;
     * joining it would silently drop the row. A new transaction always commits it.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    <S extends NotificationLog> S save(S entity);
}
