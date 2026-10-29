package com.oneshop.catalog;

import com.oneshop.customer.ProductReview;
import com.oneshop.customer.ProductReviewRepository;
import com.oneshop.inventory.BranchInventory;
import com.oneshop.inventory.InventoryService;
import com.oneshop.order.OrderRepository;
import com.oneshop.security.SecurityUtils;
import com.oneshop.user.User;
import com.oneshop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.oneshop.order.OrderItemRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final ProductReviewRepository productReviewRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final com.oneshop.customer.WishlistRepository wishlistRepository;

    @GetMapping("/products")
    public String catalog(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) Long categoryId,
            @RequestParam(value = "brand", required = false) Long brandId,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "sort", defaultValue = "newest") String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) {
                keyword = null;
            }
        }

        Sort sortObj = switch (sort) {
            case "price_asc" -> Sort.by("price").ascending();
            case "price_desc" -> Sort.by("price").descending();
            case "name_asc" -> Sort.by("name").ascending();
            default -> Sort.by("createdAt").descending();
        };

        Pageable pageable = PageRequest.of(Math.max(0, page), 12, sortObj);
        Page<Product> productPage = catalogService.searchProducts(keyword, categoryId, brandId, minPrice, maxPrice, pageable);

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

        model.addAttribute("productPage", productPage);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("soldMap", soldMap);
        model.addAttribute("avgRatingMap", avgRatingMap);
        model.addAttribute("reviewCountMap", reviewCountMap);
        model.addAttribute("categories", catalogService.getActiveCategories());
        model.addAttribute("brands", catalogService.getActiveBrands());
        model.addAttribute("selectedCategory", categoryId);
        model.addAttribute("selectedBrand", brandId);
        model.addAttribute("selectedSort", sort);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("keyword", keyword);

        return "store/catalog";
    }

    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable("slug") String slug, Model model) {
        Product product = catalogService.getProductBySlug(slug);
        List<BranchInventory> branchStocks = inventoryService.getProductStockAcrossBranches(product.getId());
        List<Product> relatedProducts = catalogService.getRelatedProducts(product.getCategory().getId(), product.getId(), 4);

        int totalAvailableStock = branchStocks.stream().mapToInt(BranchInventory::getAvailableQuantity).sum();

        // Product reviews & ratings
        List<ProductReview> reviews = productReviewRepository.findByProductIdAndActiveTrueOrderByCreatedAtDesc(product.getId());
        Double avgRatingRaw = productReviewRepository.getAverageRating(product.getId());
        Double avgRating = avgRatingRaw != null ? Math.round(avgRatingRaw * 10.0) / 10.0 : null;
        long reviewCount = productReviewRepository.countByProductIdAndActiveTrue(product.getId());
        long soldCount = orderItemRepository.countTotalSoldByProductId(product.getId());

        boolean canReview = false;
        boolean alreadyReviewed = false;
        ProductReview userReview = null;

        boolean inWishlist = false;
        var currentUserOpt = SecurityUtils.getCurrentUser();
        if (currentUserOpt.isPresent()) {
            Long userId = currentUserOpt.get().getId();
            String email = currentUserOpt.get().getUsername();
            canReview = orderRepository.hasUserPurchasedAndReceivedProduct(userId, email, product.getId());
            var existingReview = productReviewRepository.findByUserIdAndProductId(userId, product.getId());
            if (existingReview.isPresent()) {
                alreadyReviewed = true;
                userReview = existingReview.get();
            }
            inWishlist = wishlistRepository.existsByUserIdAndProductId(userId, product.getId());
        }

        model.addAttribute("product", product);
        model.addAttribute("productImages", catalogService.getProductImages(product.getId()));
        model.addAttribute("branchStocks", branchStocks);
        model.addAttribute("totalAvailableStock", totalAvailableStock);
        model.addAttribute("relatedProducts", relatedProducts);
        model.addAttribute("reviews", reviews);
        model.addAttribute("avgRating", avgRating);
        model.addAttribute("reviewCount", reviewCount);
        model.addAttribute("soldCount", soldCount);
        model.addAttribute("canReview", canReview);
        model.addAttribute("alreadyReviewed", alreadyReviewed);
        model.addAttribute("userReview", userReview);
        model.addAttribute("inWishlist", inWishlist);

        return "store/product-detail";
    }

    @PostMapping("/products/{id}/reviews")
    public String submitReview(
            @PathVariable("id") Long productId,
            @RequestParam("rating") int rating,
            @RequestParam("comment") String comment,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            RedirectAttributes redirectAttributes) {

        var currentUserOpt = SecurityUtils.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập để đánh giá sản phẩm.");
            return "redirect:/auth/login";
        }

        Product product = catalogService.getProductById(productId);
        Long userId = currentUserOpt.get().getId();
        String email = currentUserOpt.get().getUsername();

        boolean eligible = orderRepository.hasUserPurchasedAndReceivedProduct(userId, email, productId);
        if (!eligible) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Chỉ khách hàng đã mua và nhận hàng thành công mới có thể đánh giá sản phẩm này.");
            if (redirectUrl != null && !redirectUrl.isBlank()) {
                return "redirect:" + redirectUrl;
            }
            return "redirect:/products/" + product.getSlug() + "#reviewsSection";
        }

        rating = Math.max(1, Math.min(5, rating));
        User user = userRepository.findById(userId).orElseThrow();

        ProductReview review = productReviewRepository.findByUserIdAndProductId(userId, productId)
                .orElse(ProductReview.builder()
                        .user(user)
                        .product(product)
                        .build());

        review.setRating(rating);
        review.setComment(comment != null ? comment.trim() : "");
        review.setActive(true);
        review.setCreatedAt(LocalDateTime.now());
        productReviewRepository.save(review);

        redirectAttributes.addFlashAttribute("successMessage",
                "Cảm ơn bạn! Đánh giá " + rating + " sao cho sản phẩm \"" + product.getName() + "\" đã được ghi nhận thành công.");
        
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/products/" + product.getSlug() + "#reviewsSection";
    }

    @GetMapping("/categories/{slug}")
    public String categoryProducts(
            @PathVariable("slug") String slug,
            @RequestParam(value = "brand", required = false) Long brandId,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "sort", defaultValue = "newest") String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {
        Category category = catalogService.getCategoryBySlug(slug);
        return catalog(null, category.getId(), brandId, minPrice, maxPrice, sort, page, model);
    }

    @GetMapping("/brands/{slug}")
    public String brandProducts(
            @PathVariable("slug") String slug,
            @RequestParam(value = "category", required = false) Long categoryId,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "sort", defaultValue = "newest") String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {
        Brand brand = catalogService.getBrandBySlug(slug);
        return catalog(null, categoryId, brand.getId(), minPrice, maxPrice, sort, page, model);
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "category", required = false) Long categoryId,
            @RequestParam(value = "brand", required = false) Long brandId,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "sort", defaultValue = "newest") String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {
        return catalog(query, categoryId, brandId, minPrice, maxPrice, sort, page, model);
    }
}
