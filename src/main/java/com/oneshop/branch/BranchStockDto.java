package com.oneshop.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchStockDto {
    private Long branchId;
    private String branchCode;
    private String branchName;
    private String branchAddress;
    private String branchPhone;
    private Integer quantityOnHand;
    private Integer availableQuantity;
    private boolean inStock;
}
