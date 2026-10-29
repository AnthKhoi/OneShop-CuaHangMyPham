package com.oneshop.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    // Thuật toán FEFO (First-Expired, First-Out): Lô có hạn sử dụng gần nhất xuất trước
    @Query("SELECT b FROM StockBatch b " +
           "WHERE b.branch.id = :branchId AND b.product.id = :productId " +
           "AND b.quantityRemaining > 0 AND b.expiryDate >= CURRENT_DATE " +
           "ORDER BY b.expiryDate ASC")
    List<StockBatch> findAvailableBatchesFEFO(@Param("branchId") Long branchId, @Param("productId") Long productId);

    // Cảnh báo các lô hàng sắp hết hạn trong vòng N ngày
    @Query("SELECT b FROM StockBatch b " +
           "JOIN FETCH b.product p " +
           "JOIN FETCH b.branch br " +
           "WHERE (:branchId IS NULL OR br.id = :branchId) " +
           "AND b.quantityRemaining > 0 " +
           "AND b.expiryDate BETWEEN CURRENT_DATE AND :thresholdDate " +
           "ORDER BY b.expiryDate ASC")
    List<StockBatch> findExpiringBatches(@Param("branchId") Long branchId, @Param("thresholdDate") LocalDate thresholdDate);

    List<StockBatch> findByBranchIdAndProductIdOrderByExpiryDateAsc(Long branchId, Long productId);
}
