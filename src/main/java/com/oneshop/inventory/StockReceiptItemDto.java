package com.oneshop.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReceiptItemDto {
    private Long productId;
    private Long variantId;
    private String batchCode;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private Integer quantity;
    private BigDecimal costPrice;
}
