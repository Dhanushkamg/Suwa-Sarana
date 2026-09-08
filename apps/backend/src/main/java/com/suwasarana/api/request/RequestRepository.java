package com.suwasarana.api.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestRepository extends JpaRepository<BloodRequest, Long> {

    @Query("SELECT r FROM BloodRequest r WHERE r.status IN ('OPEN', 'ESCALATING')")
    List<BloodRequest> findActiveRequests();

    @Modifying
    @Query("UPDATE BloodRequest r SET r.currentRadiusKm = :radius WHERE r.id = :id")
    void updateRadius(@Param("id") Long id, @Param("radius") short radius);
    
    @Modifying
    @Query("UPDATE BloodRequest r SET r.status = :status WHERE r.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") RequestStatus status);
}
