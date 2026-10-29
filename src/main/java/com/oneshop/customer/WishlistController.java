package com.oneshop.customer;

import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import com.oneshop.security.SecurityUtils;
import com.oneshop.user.User;
import com.oneshop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String viewWishlist(Model model) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<WishlistItem> items = wishlistRepository.findByUserIdOrderByCreatedAtDesc(userId);
        model.addAttribute("items", items);
        return "store/wishlist";
    }

    @PostMapping("/toggle")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleWishlist(@RequestParam("productId") Long productId) {
        Map<String, Object> response = new HashMap<>();
        Long userId = SecurityUtils.getCurrentUserId();

        if (userId == null) {
            response.put("success", false);
            response.put("requireLogin", true);
            response.put("message", "Vui lòng đăng nhập để lưu sản phẩm yêu thích.");
            return ResponseEntity.status(401).body(response);
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            response.put("success", false);
            response.put("message", "Sản phẩm không tồn tại.");
            return ResponseEntity.badRequest().body(response);
        }

        boolean exists = wishlistRepository.existsByUserIdAndProductId(userId, productId);
        boolean liked;
        if (exists) {
            wishlistRepository.deleteByUserIdAndProductId(userId, productId);
            liked = false;
        } else {
            User user = userRepository.findById(userId).orElseThrow();
            wishlistRepository.save(WishlistItem.builder()
                    .user(user)
                    .product(product)
                    .build());
            liked = true;
        }

        long totalCount = wishlistRepository.countByUserId(userId);
        response.put("success", true);
        response.put("liked", liked);
        response.put("totalCount", totalCount);
        response.put("message", liked ? "Đã thêm vào danh sách yêu thích!" : "Đã bỏ khỏi danh sách yêu thích.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/remove/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public String removeFromWishlist(@PathVariable Long productId, RedirectAttributes redirectAttributes) {
        Long userId = SecurityUtils.getCurrentUserId();
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi danh sách yêu thích.");
        return "redirect:/wishlist";
    }
}
