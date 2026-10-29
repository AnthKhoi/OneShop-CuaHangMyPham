package com.oneshop.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT o FROM Order o WHERE (:branchId IS NULL OR o.branch.id = :branchId) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(o.recipientName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(o.recipientPhone) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Order> filterOrders(
            @Param("branchId") Long branchId,
            @Param("status") OrderStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'COMPLETED' " +
           "AND (:branchId IS NULL OR o.branch.id = :branchId)")
    BigDecimal calculateTotalRevenue(@Param("branchId") Long branchId);

    @Query("SELECT COUNT(o) FROM Order o WHERE (:branchId IS NULL OR o.branch.id = :branchId)")
    long countOrders(@Param("branchId") Long branchId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status " +
           "AND (:branchId IS NULL OR o.branch.id = :branchId)")
    long countOrdersByStatus(@Param("branchId") Long branchId, @Param("status") OrderStatus status);

    @Query("SELECT o FROM Order o WHERE (:branchId IS NULL OR o.branch.id = :branchId) ORDER BY o.createdAt DESC")
    List<Order> findRecentOrders(@Param("branchId") Long branchId, Pageable pageable);

    @Query("SELECT o.branch.id, o.branch.code, o.branch.name, COALESCE(SUM(o.totalAmount), 0), COUNT(o) " +
           "FROM Order o WHERE o.status = 'COMPLETED' " +
           "GROUP BY o.branch.id, o.branch.code, o.branch.name " +
           "ORDER BY SUM(o.totalAmount) DESC")
    List<Object[]> calculateRevenueByBranch();

    @Query("SELECT COUNT(o) > 0 FROM Order o JOIN o.items item " +
           "WHERE (o.user.id = :userId OR (o.user IS NULL AND :email IS NOT NULL AND LOWER(o.recipientEmail) = LOWER(:email))) " +
           "AND item.product.id = :productId " +
           "AND (o.status = com.oneshop.order.OrderStatus.COMPLETED OR o.status = com.oneshop.order.OrderStatus.PICKED_UP)")
    boolean hasUserPurchasedAndReceivedProduct(@Param("userId") Long userId, @Param("email") String email, @Param("productId") Long productId);

    List<Order> findByShipperIdOrderByCreatedAtDesc(Long shipperId);

    long countByShipperIdAndStatus(Long shipperId, OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.fulfillmentType = 'DELIVERY' AND o.status = 'CONFIRMED' AND o.shipper IS NULL ORDER BY o.createdAt ASC")
    List<Order> findAvailableDeliveryOrders();

    @Query("SELECT o FROM Order o WHERE o.shipper.id = :shipperId AND o.status = :status ORDER BY o.createdAt DESC")
    List<Order> findByShipperIdAndStatus(@Param("shipperId") Long shipperId, @Param("status") OrderStatus status);
}

