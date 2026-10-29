package com.oneshop.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchInventoryRepository extends JpaRepository<BranchInventory, Long> {

    Optional<BranchInventory> findByBranchIdAndProductId(Long branchId, Long productId);

    List<BranchInventory> findByProductId(Long productId);

    List<BranchInventory> findByBranchId(Long branchId);

    @Query("SELECT bi FROM BranchInventory bi WHERE bi.branch.id = :branchId AND (bi.quantityOnHand - bi.quantityReserved) <= bi.reorderLevel")
    List<BranchInventory> findLowStockByBranch(@Param("branchId") Long branchId);

    @Query("SELECT bi FROM BranchInventory bi WHERE (bi.quantityOnHand - bi.quantityReserved) <= bi.reorderLevel")
    List<BranchInventory> findAllLowStock();

    @Query("SELECT bi FROM BranchInventory bi WHERE (:branchId IS NULL OR bi.branch.id = :branchId) " +
           "AND (:keyword IS NULL OR LOWER(bi.product.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(bi.product.sku) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<BranchInventory> filterInventory(@Param("branchId") Long branchId, @Param("keyword") String keyword);
}
