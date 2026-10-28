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

public interface InventoryMovementRepository extends JpaRepository<InventoryModel.Movement, Long> {

    List<InventoryModel.Movement> findByOwnerUserIdOrderByIdDesc(Long ownerUserId, Pageable pageable);
}
