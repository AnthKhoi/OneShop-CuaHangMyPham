package com.oneshop.cart;

import com.oneshop.security.SecurityUtils;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public String viewCart(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (!SecurityUtils.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập tài khoản để xem giỏ hàng của bạn!");
            return "redirect:/auth/login?redirect=/cart";
        }
        if (SecurityUtils.isCurrentUserAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản Quản trị viên (Admin) không thực hiện mua hàng bán lẻ.");
            return "redirect:/admin";
        }
        CartDto cart = cartService.getCart(session);
        model.addAttribute("cart", cart);
        return "store/cart";
    }

    @PostMapping("/add")
    public String addToCart(
            @RequestParam("productId") Long productId,
            @RequestParam(value = "quantity", defaultValue = "1") int quantity,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestHeader(value = "Referer", required = false) String referer,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!SecurityUtils.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập tài khoản để thêm sản phẩm vào giỏ hàng!");
            String safePath = resolveSafeRedirect(referer, "/cart");
            return "redirect:/auth/login?redirect=" + URLEncoder.encode(safePath, StandardCharsets.UTF_8);
        }

        if (SecurityUtils.isCurrentUserAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản Quản trị viên không thực hiện đặt hàng. Vui lòng sử dụng tài khoản khách hàng để mua sắm.");
            return "redirect:" + resolveSafeRedirect(referer, "/products");
        }

        try {
            cartService.addToCart(session, productId, quantity, branchId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm sản phẩm vào giỏ hàng thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:" + resolveSafeRedirect(referer, "/products");
    }

    private String resolveSafeRedirect(String referer, String defaultPath) {
        if (!org.springframework.util.StringUtils.hasText(referer)) {
            return defaultPath;
        }
        try {
            java.net.URI uri = java.net.URI.create(referer.trim());
            String path = uri.getPath();
            if (org.springframework.util.StringUtils.hasText(path) && path.startsWith("/") && !path.startsWith("//") && !path.contains("\\") && !path.contains("/auth/")) {
                if (org.springframework.util.StringUtils.hasText(uri.getQuery())) {
                    return path + "?" + uri.getQuery();
                }
                return path;
            }
        } catch (Exception ignored) {
        }
        return defaultPath;
    }

    @PostMapping("/update")
    public String updateQuantity(
            @RequestParam("productId") Long productId,
            @RequestParam("quantity") int quantity,
            @RequestParam(value = "branchId", required = false) Long branchId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!SecurityUtils.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập tài khoản để cập nhật giỏ hàng!");
            return "redirect:/auth/login?redirect=/cart";
        }

        try {
            cartService.updateQuantity(session, productId, quantity, branchId);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật số lượng thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeFromCart(
            @RequestParam("productId") Long productId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!SecurityUtils.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập tài khoản để thao tác giỏ hàng!");
            return "redirect:/auth/login?redirect=/cart";
        }

        cartService.removeFromCart(session, productId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi giỏ hàng.");
        return "redirect:/cart";
    }

    @PostMapping("/apply-coupon")
    public String applyCoupon(
            @RequestParam("couponCode") String couponCode,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!SecurityUtils.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập tài khoản để áp dụng mã giảm giá!");
            return "redirect:/auth/login?redirect=/cart";
        }

        try {
            cartService.applyCoupon(session, couponCode);
            redirectAttributes.addFlashAttribute("successMessage", "Áp dụng mã giảm giá '" + couponCode + "' thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/cart";
    }

    @PostMapping("/remove-coupon")
    public String removeCoupon(HttpSession session, RedirectAttributes redirectAttributes) {
        if (!SecurityUtils.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập tài khoản để gỡ mã giảm giá!");
            return "redirect:/auth/login?redirect=/cart";
        }

        cartService.removeCoupon(session);
        redirectAttributes.addFlashAttribute("successMessage", "Đã gỡ bỏ mã giảm giá.");
        return "redirect:/cart";
    }
}
