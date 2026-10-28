package com.suwasarana.api.inventory;

import com.suwasarana.api.inventory.InventoryModel.BatchStatus;
import com.suwasarana.api.inventory.InventoryModel.Component;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryUnitRepository extends JpaRepository<InventoryModel.Unit, Long> {

    List<InventoryModel.Unit> findByOwnerUserIdOrderByExpiresOnAscIdAsc(Long ownerUserId);

    Optional<InventoryModel.Unit> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    List<InventoryModel.Unit> findByOwnerUserIdInAndStatus(Collection<Long> ownerUserIds, BatchStatus status);

    /** Batches of one type and component that can still be issued, earliest expiry first (first-expiry-first-out). */
    @Query("SELECT u FROM InventoryUnit u WHERE u.ownerUserId = :owner AND u.bloodType = :type AND u.component = :component "
            + "AND u.status = :status AND u.expiresOn >= :today AND u.units > 0 ORDER BY u.expiresOn ASC, u.id ASC")
    List<InventoryModel.Unit> findUsable(@Param("owner") Long owner, @Param("type") String type,
                                         @Param("component") Component component,
                                         @Param("today") LocalDate today, @Param("status") BatchStatus status);

    /** Batches with the given status whose expiry date is before {@code today} (expiry job passes ACTIVE). */
    @Query("SELECT u FROM InventoryUnit u WHERE u.status = :status AND u.expiresOn < :today")
    List<InventoryModel.Unit> findPastExpiry(@Param("today") LocalDate today, @Param("status") BatchStatus status);
}
