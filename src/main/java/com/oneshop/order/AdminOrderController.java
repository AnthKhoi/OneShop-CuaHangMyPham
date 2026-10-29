package com.oneshop.order;

import com.oneshop.branch.BranchService;
import com.oneshop.security.SecurityUtils;
import com.oneshop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
@Slf4j
public class AdminOrderController {

    private final OrderService orderService;
    private final BranchService branchService;
    private final PaymentService paymentService;

    @GetMapping
    public String listOrders(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "status", required = false) OrderStatus status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        branchId = SecurityUtils.enforceBranchAccess(branchId);
        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);

        Page<Order> orderPage = orderService.filterOrders(
                branchId, status, keyword,
                PageRequest.of(page, 15, Sort.by("createdAt").descending())
        );

        model.addAttribute("orderPage", orderPage);
        model.addAttribute("orders", orderPage.getContent());
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            model.addAttribute("branches", branchService.getActiveBranches().stream()
                    .filter(b -> b.getId().equals(principal.getBranchId())).toList());
        } else {
            model.addAttribute("branches", branchService.getActiveBranches());
        }
        model.addAttribute("orderStatuses", OrderStatus.values());
        model.addAttribute("selectedBranchId", branchId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("keyword", keyword);

        return "admin/orders/list";
    }

    @GetMapping("/{id}")
    public String orderDetail(@PathVariable("id") Long id, Model model) {
        Order order = orderService.getOrderById(id);
        SecurityUtils.enforceBranchAccess(order.getBranch().getId());

        model.addAttribute("order", order);

        // Filter order statuses based on fulfillment type
        java.util.List<OrderStatus> filteredStatuses = new java.util.ArrayList<>();
        if (order.getFulfillmentType() == FulfillmentType.DELIVERY) {
            // Giao hàng tận nơi: Bỏ READY_FOR_PICKUP và PICKED_UP
            for (OrderStatus s : OrderStatus.values()) {
                if (s != OrderStatus.READY_FOR_PICKUP && s != OrderStatus.PICKED_UP) {
                    filteredStatuses.add(s);
                }
            }
        } else {
            // Nhận tại cửa hàng: Bỏ SHIPPED (Đang giao hàng)
            for (OrderStatus s : OrderStatus.values()) {
                if (s != OrderStatus.SHIPPED) {
                    filteredStatuses.add(s);
                }
            }
        }
        model.addAttribute("orderStatuses", filteredStatuses);
        model.addAttribute("paymentStatuses", PaymentStatus.values());
        model.addAttribute("statusHistories", orderService.getOrderStatusHistory(id));
        model.addAttribute("payments", paymentService.getPaymentsByOrder(id));

        return "admin/orders/detail";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") OrderStatus status,
            RedirectAttributes redirectAttributes) {

        Order order = orderService.getOrderById(id);
        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            if (!order.getBranch().getId().equals(principal.getBranchId())) {
                throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn hàng của chi nhánh khác");
            }
        }

        try {
            String actor = principal != null ? principal.getFullName() : "Admin";
            orderService.updateOrderStatus(id, status, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái đơn hàng thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/{id}/payment-status")
    public String updatePaymentStatus(
            @PathVariable("id") Long id,
            @RequestParam("paymentStatus") PaymentStatus paymentStatus,
            RedirectAttributes redirectAttributes) {

        Order order = orderService.getOrderById(id);
        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            if (!order.getBranch().getId().equals(principal.getBranchId())) {
                throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn hàng của chi nhánh khác");
            }
        }

        try {
            String actor = principal != null ? principal.getFullName() : "Admin";
            orderService.updatePaymentStatus(id, paymentStatus, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái thanh toán thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/orders/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancelOrder(
            @PathVariable("id") Long id,
            @RequestParam("cancelReason") String cancelReason,
            RedirectAttributes redirectAttributes) {

        Order order = orderService.getOrderById(id);
        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            if (!order.getBranch().getId().equals(principal.getBranchId())) {
                throw new AccessDeniedException("Bạn không có quyền thao tác trên đơn hàng của chi nhánh khác");
            }
        }

        try {
            String actor = principal != null ? principal.getFullName() : "Admin";
            orderService.cancelOrder(id, cancelReason, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy đơn hàng và hoàn trả tồn kho thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/orders/" + id;
    }
}
