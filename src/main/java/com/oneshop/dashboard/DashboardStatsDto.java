package com.oneshop.dashboard;

import com.oneshop.order.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    private BigDecimal totalRevenue;
    private long totalOrders;
    private long pendingOrders;
    private long completedOrders;
    private long cancelledOrders;
    private long lowStockCount;
    private int expiringBatchesCount;
    private BigDecimal averageOrderValue;
    private List<Order> recentOrders;
    private List<BranchRevenueDto> branchRevenues;
    private List<TopProductDto> topSellingProducts;
}
