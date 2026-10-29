package com.oneshop.dashboard;

import com.oneshop.inventory.InventoryService;
import com.oneshop.order.OrderItemRepository;
import com.oneshop.order.Order;
import com.oneshop.order.OrderRepository;
import com.oneshop.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final com.oneshop.catalog.ProductRepository productRepository;

    public DashboardStatsDto getDashboardStats(Long branchId) {
        BigDecimal totalRevenue = orderRepository.calculateTotalRevenue(branchId);
        long totalOrders = orderRepository.countOrders(branchId);
        long pendingOrders = orderRepository.countOrdersByStatus(branchId, OrderStatus.PENDING);
        long completedOrders = orderRepository.countOrdersByStatus(branchId, OrderStatus.COMPLETED);
        long cancelledOrders = orderRepository.countOrdersByStatus(branchId, OrderStatus.CANCELLED);
        long lowStockCount = inventoryService.getLowStockAlerts(branchId).size();
        int expiringBatchesCount = inventoryService.getExpiringBatches(branchId, 30).size();

        BigDecimal aov = BigDecimal.ZERO;
        if (completedOrders > 0) {
            aov = totalRevenue.divide(BigDecimal.valueOf(completedOrders), 0, RoundingMode.HALF_UP);
        }

        List<Order> recentOrders = orderRepository.findRecentOrders(branchId, PageRequest.of(0, 8));

        // Aggregate Top Selling Products
        List<Object[]> topProductRows = orderItemRepository.findTopSellingProducts(branchId, PageRequest.of(0, 5));
        List<TopProductDto> topSellingProducts = new ArrayList<>();
        for (Object[] row : topProductRows) {
            String productName = (String) row[0];
            String sku = (String) row[1];
            Long totalSold = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            BigDecimal revenue = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;
            String imageUrl = row.length > 4 ? (String) row[4] : null;

            if ((imageUrl == null || imageUrl.isBlank()) && sku != null) {
                imageUrl = productRepository.findBySku(sku)
                        .map(com.oneshop.catalog.Product::getPrimaryImageUrl)
                        .orElse(null);
            }

            topSellingProducts.add(TopProductDto.builder()
                    .productName(productName)
                    .sku(sku)
                    .totalSold(totalSold)
                    .totalRevenue(revenue)
                    .imageUrl(imageUrl)
                    .build());
        }

        // Aggregate Revenue By Branch (Only relevant when viewing all branches or for admin)
        List<Object[]> branchRevenueRows = orderRepository.calculateRevenueByBranch();
        List<BranchRevenueDto> branchRevenues = new ArrayList<>();
        for (Object[] row : branchRevenueRows) {
            Long bId = ((Number) row[0]).longValue();
            String bCode = (String) row[1];
            String bName = (String) row[2];
            BigDecimal rev = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;
            Long count = row[4] != null ? ((Number) row[4]).longValue() : 0L;

            branchRevenues.add(BranchRevenueDto.builder()
                    .branchId(bId)
                    .branchCode(bCode)
                    .branchName(bName)
                    .revenue(rev)
                    .orderCount(count)
                    .build());
        }

        return DashboardStatsDto.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .pendingOrders(pendingOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .lowStockCount(lowStockCount)
                .expiringBatchesCount(expiringBatchesCount)
                .averageOrderValue(aov)
                .recentOrders(recentOrders)
                .branchRevenues(branchRevenues)
                .topSellingProducts(topSellingProducts)
                .build();
    }
}
