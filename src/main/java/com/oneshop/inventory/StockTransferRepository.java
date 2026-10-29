package com.oneshop.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    Optional<StockTransfer> findByTransferCode(String transferCode);

    @Query("SELECT st FROM StockTransfer st WHERE (:branchId IS NULL OR st.sourceBranch.id = :branchId OR st.destinationBranch.id = :branchId) " +
           "ORDER BY st.createdAt DESC")
    List<StockTransfer> findByBranch(@Param("branchId") Long branchId);
}
