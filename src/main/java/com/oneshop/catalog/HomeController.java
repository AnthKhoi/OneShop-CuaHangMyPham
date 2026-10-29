package com.oneshop.catalog;

import com.oneshop.branch.BranchService;
import com.oneshop.customer.ProductReviewRepository;
import com.oneshop.order.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CatalogService catalogService;
    private final BranchService branchService;
    private final OrderItemRepository orderItemRepository;
    private final ProductReviewRepository productReviewRepository;

    @GetMapping("/")
    public String index(Model model) {
        // Product Sold Counts Map
        Map<Long, Long> soldMap = new HashMap<>();
        for (Object[] row : orderItemRepository.countTotalSoldGroupedByProduct()) {
            if (row[0] != null && row[1] != null) {
                soldMap.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }

        // Product Rating & Review Counts Map
        Map<Long, Double> avgRatingMap = new HashMap<>();
        Map<Long, Long> reviewCountMap = new HashMap<>();
        for (Object[] row : productReviewRepository.getRatingStatsGroupedByProduct()) {
            if (row[0] != null) {
                Long pId = ((Number) row[0]).longValue();
                Double avg = row[1] != null ? Math.round(((Number) row[1]).doubleValue() * 10.0) / 10.0 : null;
                Long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                if (avg != null) avgRatingMap.put(pId, avg);
                reviewCountMap.put(pId, count);
            }
        }

        // Flash Deal 2-Hour Slot Rotation
        int hour = java.time.LocalTime.now().getHour();
        int slotIndex = hour / 2; // 0 to 11
        int startHour = slotIndex * 2;
        int endHour = startHour + 2;
        String flashDealTimeRange = String.format("%02d:00 - %02d:00", startHour, endHour);

        List<Product> allActiveProducts = catalogService.getAllProducts().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .toList();

        // Flash Deal MUST only showcase products with actual discounts (hasDiscount == true)
        List<Product> discountedProducts = allActiveProducts.stream()
                .filter(Product::hasDiscount)
                .toList();

        // Rotate products deterministically based on today's day of year and 2-hour slot
        int seed = java.time.LocalDate.now().getDayOfYear() * 12 + slotIndex;
        List<Product> flashPool = new ArrayList<>(discountedProducts);
        Collections.shuffle(flashPool, new Random(seed));
        List<Product> flashDealProducts = flashPool.stream().limit(8).toList();

        // Curate "Gợi ý hôm nay"
        List<Product> recPool = new ArrayList<>(allActiveProducts);
        Collections.shuffle(recPool, new Random(seed + 999));
        List<Product> dailyRecommendations = recPool.stream().limit(8).toList();

        model.addAttribute("soldMap", soldMap);
        model.addAttribute("avgRatingMap", avgRatingMap);
        model.addAttribute("reviewCountMap", reviewCountMap);
        model.addAttribute("flashDealProducts", flashDealProducts);
        model.addAttribute("flashDealTimeRange", flashDealTimeRange);
        model.addAttribute("dailyRecommendations", dailyRecommendations);
        model.addAttribute("featuredProducts", flashDealProducts); // Backward-compatibility
        model.addAttribute("newArrivals", dailyRecommendations);    // Backward-compatibility
        model.addAttribute("categories", catalogService.getRootCategories());
        model.addAttribute("brands", catalogService.getActiveBrands());
        model.addAttribute("branches", branchService.getActiveBranches());
        return "store/index";
    }
}
