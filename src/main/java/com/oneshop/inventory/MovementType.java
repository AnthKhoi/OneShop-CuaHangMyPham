package com.oneshop.inventory;

public enum MovementType {
    IMPORT("Nhập kho"),
    EXPORT("Xuất kho"),
    ADJUSTMENT("Điều chỉnh tồn kho"),
    SALE("Bán hàng - Xuất theo đơn"),
    RETURN("Hoàn hàng - Trả lại kho"),
    TRANSFER_OUT("Chuyển kho - Xuất đi"),
    TRANSFER_IN("Chuyển kho - Nhập đến");

    private final String description;

    MovementType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
