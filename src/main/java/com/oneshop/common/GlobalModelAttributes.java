package com.oneshop.common;

import com.oneshop.branch.BranchService;
import com.oneshop.cart.CartDto;
import com.oneshop.cart.CartService;
import com.oneshop.catalog.CatalogService;
import com.oneshop.security.SecurityUtils;
import com.oneshop.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final CatalogService catalogService;
    private final BranchService branchService;
    private final CartService cartService;
    private final com.oneshop.customer.WishlistRepository wishlistRepository;

    @ModelAttribute("currentUser")
    public UserPrincipal getCurrentUser() {
        return SecurityUtils.getCurrentUser().orElse(null);
    }

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated() {
        return SecurityUtils.isAuthenticated();
    }

    @ModelAttribute("globalCartCount")
    public int getCartCount(HttpServletRequest request) {
        if (request == null) return 0;
        HttpSession session = request.getSession(false);
        if (session == null) return 0;
        CartDto cart = (CartDto) session.getAttribute(CartService.SESSION_CART_KEY);
        return cart != null ? cart.getTotalQuantity() : 0;
    }

    @ModelAttribute("globalWishlistCount")
    public long getWishlistCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return 0;
        try {
            return wishlistRepository.countByUserId(userId);
        } catch (Exception ex) {
            return 0;
        }
    }

    @ModelAttribute("globalCategories")
    public Object getGlobalCategories() {
        return catalogService.getRootCategories();
    }

    @ModelAttribute("globalBranches")
    public Object getGlobalBranches() {
        return branchService.getActiveBranches();
    }

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "";
    }
}
