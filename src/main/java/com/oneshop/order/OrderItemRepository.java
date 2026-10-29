package com.oneshop.order;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi.productName, oi.skuSnapshot, SUM(oi.quantity), SUM(oi.lineTotal), MAX(oi.imageUrl) " +
           "FROM OrderItem oi WHERE (oi.order.status = com.oneshop.order.OrderStatus.COMPLETED OR oi.order.status = com.oneshop.order.OrderStatus.PICKED_UP) " +
           "AND (:branchId IS NULL OR oi.order.branch.id = :branchId) " +
           "GROUP BY oi.productName, oi.skuSnapshot " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingProducts(@Param("branchId") Long branchId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi WHERE oi.product.id = :productId " +
           "AND (oi.order.status = com.oneshop.order.OrderStatus.COMPLETED OR oi.order.status = com.oneshop.order.OrderStatus.PICKED_UP)")
    long countTotalSoldByProductId(@Param("productId") Long productId);

    @Query("SELECT oi.product.id, COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
           "WHERE oi.order.status = com.oneshop.order.OrderStatus.COMPLETED OR oi.order.status = com.oneshop.order.OrderStatus.PICKED_UP " +
           "GROUP BY oi.product.id")
    List<Object[]> countTotalSoldGroupedByProduct();
}
