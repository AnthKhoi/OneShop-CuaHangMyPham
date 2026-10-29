package com.oneshop.order;

import com.oneshop.branch.BranchService;
import com.oneshop.cart.CartDto;
import com.oneshop.cart.CartService;
import com.oneshop.security.SecurityUtils;
import com.oneshop.security.UserPrincipal;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;
    private final BranchService branchService;
    private final PaymentService paymentService;
    private final com.oneshop.user.UserRepository userRepository;

    @GetMapping("/checkout")
    public String checkoutPage(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (!SecurityUtils.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập tài khoản để tiến hành thanh toán!");
            return "redirect:/auth/login?redirect=/checkout";
        }

        if (SecurityUtils.isCurrentUserAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản Quản trị viên (Admin) không thực hiện mua hàng bán lẻ. Vui lòng đăng nhập tài khoản Khách hàng.");
            return "redirect:/admin/orders";
        }

        CartDto cart = cartService.getCart(session);
        if (cart.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Giỏ hàng của bạn đang trống, vui lòng chọn sản phẩm trước khi thanh toán.");
            return "redirect:/cart";
        }

        CheckoutForm form = new CheckoutForm();
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            userRepository.findById(currentUserId).ifPresent(user -> {
                form.setRecipientName(user.getFullName());
                form.setRecipientEmail(user.getEmail());
                if (user.getPhone() != null && !user.getPhone().isBlank()) {
                    form.setRecipientPhone(user.getPhone());
                }
                if (user.getAddress() != null && !user.getAddress().isBlank()) {
                    form.setShippingAddress(user.getAddress());
                }
            });
        }

        // Default branch selection to first active branch
        branchService.getActiveBranches().stream().findFirst().ifPresent(b -> form.setBranchId(b.getId()));

        model.addAttribute("cart", cart);
        model.addAttribute("checkoutForm", form);
        model.addAttribute("branches", branchService.getActiveBranches());

        return "store/checkout";
    }

    @PostMapping("/checkout")
    public String processCheckout(
            @Valid @ModelAttribute("checkoutForm") CheckoutForm checkoutForm,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (!SecurityUtils.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập để hoàn tất đơn hàng!");
            return "redirect:/auth/login?redirect=/checkout";
        }

        if (SecurityUtils.isCurrentUserAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản Quản trị viên (Admin) không thực hiện mua hàng.");
            return "redirect:/admin/orders";
        }

        CartDto cart = cartService.getCart(session);
        if (cart.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Giỏ hàng của bạn đang trống.");
            return "redirect:/cart";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("cart", cart);
            model.addAttribute("branches", branchService.getActiveBranches());
            return "store/checkout";
        }

        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Order order = orderService.createOrder(cart, checkoutForm, userId);

            // Clear cart upon successful order creation
            cartService.clearCart(session);

            return "redirect:/orders/" + order.getOrderCode() + "/confirmation?token=" + order.getAccessToken();
        } catch (Exception ex) {
            log.error("Failed to process checkout: ", ex);
            model.addAttribute("cart", cart);
            model.addAttribute("branches", branchService.getActiveBranches());
            model.addAttribute("errorMessage", ex.getMessage());
            return "store/checkout";
        }
    }

    @GetMapping("/orders/{orderCode}/confirmation")
    public String orderConfirmation(
            @PathVariable("orderCode") String orderCode,
            @RequestParam(value = "token", required = false) String token,
            Authentication authentication,
            Model model) {
        Order order = orderService.getOrderByCode(orderCode);

        // Security check: Must possess secret accessToken, or be order owner, or be admin/manager
        boolean isAuthorized = false;
        if (token != null && !token.isBlank() && token.equals(order.getAccessToken())) {
            isAuthorized = true;
        } else if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            boolean isStaff = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_BRANCH_MANAGER"));
            if (isStaff) {
                isAuthorized = true;
            } else if (order.getUser() != null && authentication.getName().equalsIgnoreCase(order.getUser().getEmail())) {
                isAuthorized = true;
            }
        }

        if (!isAuthorized) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền truy cập thông tin đơn hàng này.");
        }

        model.addAttribute("order", order);
        model.addAttribute("vietQrUrl", paymentService.generateVietQrUrl(order));
        model.addAttribute("bankInfo", paymentService.getBankInfo());
        return "store/order-confirmation";
    }
}

