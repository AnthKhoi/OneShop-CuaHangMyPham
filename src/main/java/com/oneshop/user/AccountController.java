package com.oneshop.user;

import com.oneshop.order.Order;
import com.oneshop.order.OrderService;
import com.oneshop.security.SecurityUtils;
import com.oneshop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    @GetMapping
    public String profile(Model model) {
        UserPrincipal principal = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("Vui lòng đăng nhập để xem thông tin tài khoản"));

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        model.addAttribute("user", user);
        return "store/account/profile";
    }

    @PostMapping("/update")
    public String updateProfile(
            @RequestParam("fullName") String fullName,
            @RequestParam("phone") String phone,
            @RequestParam("address") String address,
            RedirectAttributes redirectAttributes) {

        UserPrincipal principal = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("Vui lòng đăng nhập để cập nhật thông tin"));

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        user.setPhone(phone != null ? phone.trim() : null);
        user.setAddress(address != null ? address.trim() : null);

        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin và địa chỉ giao hàng thành công!");
        return "redirect:/account";
    }

    @GetMapping("/orders")
    public String myOrders(Model model) {
        if (SecurityUtils.isCurrentUserAdmin()) {
            return "redirect:/admin/orders";
        }

        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return "redirect:/auth/login";
        }

        List<Order> orders = orderService.getOrdersByUser(userId);
        model.addAttribute("orders", orders);
        return "store/account/order-history";
    }

    @GetMapping("/orders/{orderCode}")
    public String orderDetail(@PathVariable("orderCode") String orderCode, Model model) {
        Order order = orderService.getOrderByCode(orderCode);
        if (SecurityUtils.isCurrentUserAdmin()) {
            return "redirect:/admin/orders/" + order.getId();
        }

        Long userId = SecurityUtils.getCurrentUserId();
        // IDOR Protection: verify order belongs to currently logged-in customer
        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền xem đơn hàng này");
        }

        model.addAttribute("order", order);
        return "store/account/order-detail";
    }

    @PostMapping("/orders/{orderCode}/cancel")
    public String cancelMyOrder(
            @PathVariable("orderCode") String orderCode,
            @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {

        Long userId = SecurityUtils.getCurrentUserId();
        Order order = orderService.getOrderByCode(orderCode);

        if (!SecurityUtils.isCurrentUserAdmin() && (order.getUser() == null || !order.getUser().getId().equals(userId))) {
            throw new AccessDeniedException("Bạn không có quyền hủy đơn hàng này");
        }

        try {
            orderService.cancelOrder(order.getId(), reason, SecurityUtils.getCurrentUser().map(UserPrincipal::getFullName).orElse("Khách hàng"));
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy đơn hàng #" + orderCode + " thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/account/orders/" + orderCode;
    }
}
