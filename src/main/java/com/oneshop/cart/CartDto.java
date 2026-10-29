package com.oneshop.cart;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CartDto implements Serializable {

    private List<CartItemDto> items = new ArrayList<>();
    private String couponCode;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private Long selectedBranchId; // Selected pickup/processing branch

    public int getTotalQuantity() {
        return items.stream().mapToInt(CartItemDto::getQuantity).sum();
    }

    public BigDecimal getSubtotal() {
        return items.stream()
                .map(CartItemDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getShippingFee(boolean isPickup) {
        if (isPickup) return BigDecimal.ZERO;
        BigDecimal subtotal = getSubtotal();
        if (subtotal.compareTo(BigDecimal.valueOf(500000)) >= 0 || items.isEmpty()) {
            return BigDecimal.ZERO; // Free ship for orders >= 500,000 VND
        }
        return BigDecimal.valueOf(30000); // Standard shipping 30,000 VND
    }

    public BigDecimal getTotalAmount(boolean isPickup) {
        BigDecimal subtotal = getSubtotal();
        BigDecimal shipping = getShippingFee(isPickup);
        BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discount).add(shipping);
        return total.max(BigDecimal.ZERO);
    }

    public boolean isFreeShipping() {
        return getShippingFee(false).compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
