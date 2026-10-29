package com.oneshop.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("SELECT sm FROM StockMovement sm WHERE (:branchId IS NULL OR sm.branch.id = :branchId) " +
           "AND (:movementType IS NULL OR sm.movementType = :movementType) " +
           "ORDER BY sm.createdAt DESC")
    Page<StockMovement> findMovements(
            @Param("branchId") Long branchId,
            @Param("movementType") MovementType movementType,
            Pageable pageable);

    List<StockMovement> findTop10ByBranchIdOrderByCreatedAtDesc(Long branchId);
}
