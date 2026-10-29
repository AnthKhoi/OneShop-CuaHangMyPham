package com.oneshop.order;

public enum PaymentStatus {
    UNPAID("Chưa thanh toán"),
    PENDING("Chờ xác nhận thanh toán"),
    PAID("Đã thanh toán"),
    REFUNDED("Đã hoàn tiền");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
