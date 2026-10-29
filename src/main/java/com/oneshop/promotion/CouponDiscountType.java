package com.oneshop.promotion;

public enum CouponDiscountType {
    PERCENTAGE("Giảm theo %"),
    FIXED_AMOUNT("Giảm số tiền cố định (₫)");

    private final String description;

    CouponDiscountType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
