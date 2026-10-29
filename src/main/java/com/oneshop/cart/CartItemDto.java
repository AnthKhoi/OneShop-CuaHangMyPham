package com.oneshop.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDto implements Serializable {

    private Long productId;
    private String productName;
    private String sku;
    private String slug;
    private String imageUrl;
    private BigDecimal price;
    private Integer quantity;

    public BigDecimal getLineTotal() {
        if (price == null || quantity == null) return BigDecimal.ZERO;
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
