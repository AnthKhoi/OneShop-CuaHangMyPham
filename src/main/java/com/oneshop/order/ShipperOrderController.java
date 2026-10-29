package com.oneshop.order;

import com.oneshop.security.SecurityUtils;
import com.oneshop.user.User;
import com.oneshop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/shipper")
@PreAuthorize("hasAnyRole('SHIPPER', 'ADMIN')")
@RequiredArgsConstructor
public class ShipperOrderController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @GetMapping
    public String shipperDashboard(
            @RequestParam(name = "tab", defaultValue = "delivering") String tab,
            Model model) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        long deliveringCount = currentUserId != null ? orderRepository.countByShipperIdAndStatus(currentUserId, OrderStatus.SHIPPED) : 0;
        long completedCount = currentUserId != null ? orderRepository.countByShipperIdAndStatus(currentUserId, OrderStatus.COMPLETED) : 0;
        List<Order> availableOrders = orderRepository.findAvailableDeliveryOrders();
        long availableCount = availableOrders.size();

        List<Order> orders;
        if ("available".equalsIgnoreCase(tab)) {
            orders = availableOrders;
        } else if ("completed".equalsIgnoreCase(tab)) {
            orders = currentUserId != null ? orderRepository.findByShipperIdAndStatus(currentUserId, OrderStatus.COMPLETED) : List.of();
        } else {
            tab = "delivering";
            orders = currentUserId != null ? orderRepository.findByShipperIdAndStatus(currentUserId, OrderStatus.SHIPPED) : List.of();
        }

        model.addAttribute("tab", tab);
        model.addAttribute("orders", orders);
        model.addAttribute("deliveringCount", deliveringCount);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("availableCount", availableCount);

        return "shipper/index";
    }

    @PostMapping("/orders/{id}/claim")
    public String claimOrder(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng đăng nhập lại.");
            return "redirect:/auth/login";
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Đơn hàng không tồn tại."));

        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PENDING) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đơn hàng không ở trạng thái sẵn sàng giao.");
            return "redirect:/shipper?tab=available";
        }

        User shipper = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin shipper."));

        order.setShipper(shipper);
        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);

        redirectAttributes.addFlashAttribute("successMessage", "Bạn đã nhận đơn hàng #" + order.getOrderCode() + " thành công! Vui lòng tiến hành giao hàng.");
        return "redirect:/shipper?tab=delivering";
    }

    @PostMapping("/orders/{id}/complete")
    public String completeDelivery(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Đơn hàng không tồn tại."));

        if (order.getShipper() == null || (!order.getShipper().getId().equals(currentUserId) && !SecurityUtils.isCurrentUserAdmin())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền cập nhật đơn hàng này.");
            return "redirect:/shipper?tab=delivering";
        }

        order.setStatus(OrderStatus.COMPLETED);
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }
        orderRepository.save(order);

        redirectAttributes.addFlashAttribute("successMessage", "Xác nhận giao thành công đơn hàng #" + order.getOrderCode() + "!");
        return "redirect:/shipper?tab=completed";
    }
}
