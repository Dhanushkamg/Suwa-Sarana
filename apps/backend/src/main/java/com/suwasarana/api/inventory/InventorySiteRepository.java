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

public interface InventorySiteRepository extends JpaRepository<InventoryModel.Site, Long> {

    /** Sites of VERIFIED institutions that publish their status and have said where they are. */
    @Query("SELECT s FROM InventorySite s, User u WHERE u.id = s.ownerUserId "
            + "AND u.verificationStatus = :verified AND s.publicSignals = true AND s.district IS NOT NULL")
    List<InventoryModel.Site> findPublishable(@Param("verified") com.suwasarana.api.user.VerificationStatus verified);
}
