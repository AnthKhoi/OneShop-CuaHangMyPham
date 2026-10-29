package com.oneshop.order;

public enum PaymentMethod {
    COD("Thanh toán khi nhận hàng (COD)"),
    BANK_TRANSFER("Chuyển khoản ngân hàng qua mã QR"),
    STORE_PAYMENT("Thanh toán trực tiếp tại quầy OneShop");

    private final String description;

    PaymentMethod(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
