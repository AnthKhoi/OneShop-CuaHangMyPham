package com.oneshop.pos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PosOrderItemDto {
    private Long productId;
    private Integer quantity;
}
