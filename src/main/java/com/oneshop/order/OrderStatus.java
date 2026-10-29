package com.oneshop.order;

public enum OrderStatus {
    PENDING("Chờ xác nhận"),
    CONFIRMED("Đã xác nhận"),
    PROCESSING("Đang xử lý / Đóng gói"),
    SHIPPED("Đang giao hàng"),
    COMPLETED("Hoàn tất"),
    READY_FOR_PICKUP("Sẵn sàng nhận tại cửa hàng"),
    PICKED_UP("Đã nhận tại cửa hàng"),
    CANCELLED("Đã hủy");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean canCancel() {
        return this == PENDING || this == CONFIRMED || this == READY_FOR_PICKUP;
    }
}
