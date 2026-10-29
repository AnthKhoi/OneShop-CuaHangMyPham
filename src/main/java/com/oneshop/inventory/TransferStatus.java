package com.oneshop.inventory;

public enum TransferStatus {
    PENDING("Chờ xuất kho"),
    DISPATCHED("Đang vận chuyển"),
    RECEIVED("Đã nhận hàng"),
    CANCELLED("Đã hủy");

    private final String description;

    TransferStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
