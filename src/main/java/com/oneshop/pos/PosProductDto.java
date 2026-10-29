package com.oneshop.pos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosProductDto {
    private Long id;
    private String name;
    private String sku;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;
    private int availableStock;
}
