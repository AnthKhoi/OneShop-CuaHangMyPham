package com.oneshop.promotion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {
    List<CouponUsage> findByCouponId(Long couponId);
    List<CouponUsage> findByUserId(Long userId);
    List<CouponUsage> findByOrderId(Long orderId);
    long countByCouponIdAndUserId(Long couponId, Long userId);
}
