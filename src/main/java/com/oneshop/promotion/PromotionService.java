package com.oneshop.promotion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    public Coupon getCouponById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã giảm giá ID: " + id));
    }

    @Transactional
    public Coupon saveCoupon(Coupon coupon) {
        if (coupon.getCode() != null) {
            coupon.setCode(coupon.getCode().trim().toUpperCase());
        }
        if (coupon.getId() == null && couponRepository.existsByCodeIgnoreCase(coupon.getCode())) {
            throw new IllegalArgumentException("Mã voucher '" + coupon.getCode() + "' đã tồn tại");
        }
        return couponRepository.save(coupon);
    }

    @Transactional
    public Coupon toggleStatus(Long id) {
        Coupon coupon = getCouponById(id);
        coupon.setActive(!Boolean.TRUE.equals(coupon.getActive()));
        return couponRepository.save(coupon);
    }

    @Transactional
    public void deleteCoupon(Long id) {
        Coupon coupon = getCouponById(id);
        couponRepository.delete(coupon);
    }

    public List<CouponUsage> getCouponUsages(Long couponId) {
        return couponUsageRepository.findByCouponId(couponId);
    }
}
