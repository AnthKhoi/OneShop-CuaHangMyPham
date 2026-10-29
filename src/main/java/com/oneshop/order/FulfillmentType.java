package com.oneshop.order;

public enum FulfillmentType {
    DELIVERY("Giao hàng tận nơi"),
    PICKUP("Nhận tại cửa hàng OneShop");

    private final String description;

    FulfillmentType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
