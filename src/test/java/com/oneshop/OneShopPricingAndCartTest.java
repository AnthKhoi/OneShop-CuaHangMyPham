package com.oneshop;

import com.oneshop.cart.CartDto;
import com.oneshop.cart.CartItemDto;
import com.oneshop.promotion.Coupon;
import com.oneshop.promotion.CouponDiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests — Backend Pricing, Discounts & Cart Rules")
class OneShopPricingAndCartTest {

    @Test
    @DisplayName("TC-01: Tính chính xác subtotal và tổng tiền từ danh sách sản phẩm")
    void testSubtotalAndTotalCalculation() {
        CartDto cart = new CartDto();

        // Item 1: 420.000 x 2 = 840.000
        cart.getItems().add(CartItemDto.builder()
                .productId(1L)
                .productName("Kem Chống Nắng LRP")
                .sku("LRP-001")
                .price(BigDecimal.valueOf(420000))
                .quantity(2)
                .build());

        // Item 2: 590.000 x 1 = 590.000
        cart.getItems().add(CartItemDto.builder()
                .productId(2L)
                .productName("Son MAC Chili")
                .sku("MAC-003")
                .price(BigDecimal.valueOf(590000))
                .quantity(1)
                .build());

        // Subtotal = 840.000 + 590.000 = 1.430.000
        assertEquals(BigDecimal.valueOf(1430000), cart.getSubtotal());

        // Subtotal >= 500.000 => Shipping fee is 0
        assertEquals(BigDecimal.ZERO, cart.getShippingFee(false));

        // Total = 1.430.000
        assertEquals(BigDecimal.valueOf(1430000), cart.getTotalAmount(false));
    }

    @Test
    @DisplayName("TC-02: Phí vận chuyển 30.000đ cho đơn hàng dưới 500.000đ khi giao hàng tận nơi")
    void testShippingFeeForSmallOrders() {
        CartDto cart = new CartDto();
        cart.getItems().add(CartItemDto.builder()
                .productId(1L)
                .productName("Sữa rửa mặt")
                .price(BigDecimal.valueOf(310000))
                .quantity(1)
                .build());

        assertEquals(BigDecimal.valueOf(310000), cart.getSubtotal());
        // Delivery fee = 30.000
        assertEquals(BigDecimal.valueOf(30000), cart.getShippingFee(false));
        // Total = 340.000
        assertEquals(BigDecimal.valueOf(340000), cart.getTotalAmount(false));

        // Pickup at branch fee = 0
        assertEquals(BigDecimal.ZERO, cart.getShippingFee(true));
        assertEquals(BigDecimal.valueOf(310000), cart.getTotalAmount(true));
    }

    @Test
    @DisplayName("TC-03: Áp dụng mã giảm giá phần trăm có giới hạn tối đa (Max Discount)")
    void testCouponPercentageCalculationWithCap() {
        Coupon coupon = Coupon.builder()
                .code("ONESHOP10")
                .discountType(CouponDiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10)) // 10%
                .minOrderAmount(BigDecimal.valueOf(500000))
                .maxDiscountAmount(BigDecimal.valueOf(100000)) // Max 100k
                .active(true)
                .build();

        // Case A: Order 800.000 => 10% = 80.000 (< 100.000 cap)
        BigDecimal discountA = coupon.calculateDiscount(BigDecimal.valueOf(800000));
        assertEquals(0, BigDecimal.valueOf(80000).compareTo(discountA));

        // Case B: Order 2.000.000 => 10% = 200.000 (> 100.000 cap) => capped at 100.000
        BigDecimal discountB = coupon.calculateDiscount(BigDecimal.valueOf(2000000));
        assertEquals(0, BigDecimal.valueOf(100000).compareTo(discountB));

        // Case C: Order 400.000 (< 500.000 min order) => invalid => 0
        assertFalse(coupon.isValid(BigDecimal.valueOf(400000)));
        assertEquals(BigDecimal.ZERO, coupon.calculateDiscount(BigDecimal.valueOf(400000)));
    }

    @Test
    @DisplayName("TC-04: Áp dụng mã giảm giá số tiền cố định (Fixed Amount)")
    void testFixedAmountCoupon() {
        Coupon coupon = Coupon.builder()
                .code("WELCOME50")
                .discountType(CouponDiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(50000))
                .minOrderAmount(BigDecimal.valueOf(300000))
                .active(true)
                .build();

        assertTrue(coupon.isValid(BigDecimal.valueOf(350000)));
        assertEquals(BigDecimal.valueOf(50000), coupon.calculateDiscount(BigDecimal.valueOf(350000)));
    }
}
