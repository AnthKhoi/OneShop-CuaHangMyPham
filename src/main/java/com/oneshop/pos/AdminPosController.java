package com.oneshop.pos;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchRepository;
import com.oneshop.branch.BranchService;
import com.oneshop.catalog.CategoryRepository;
import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import com.oneshop.inventory.BranchInventory;
import com.oneshop.inventory.BranchInventoryRepository;
import com.oneshop.order.Order;
import com.oneshop.order.OrderRepository;
import com.oneshop.order.Payment;
import com.oneshop.order.PaymentRepository;
import com.oneshop.security.SecurityUtils;
import com.oneshop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/pos")
@RequiredArgsConstructor
@Slf4j
public class AdminPosController {

    private final PosService posService;
    private final BranchService branchService;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final BranchInventoryRepository inventoryRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final com.oneshop.user.UserRepository userRepository;

    @GetMapping
    public String posTerminal(
            @RequestParam(value = "branchId", required = false) Long branchId,
            Model model) {

        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);

        // Enforce branch scope for branch employees
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            branchId = principal.getBranchId();
        } else if (branchId == null) {
            List<Branch> activeBranches = branchService.getActiveBranches();
            if (!activeBranches.isEmpty()) {
                branchId = activeBranches.get(0).getId();
            }
        }

        Branch currentBranch = null;
        if (branchId != null) {
            currentBranch = branchRepository.findById(branchId).orElse(null);
        }

        // Fetch stock mapping for this branch
        Map<Long, Integer> stockMap = new HashMap<>();
        if (branchId != null) {
            List<BranchInventory> branchInventories = inventoryRepository.findByBranchId(branchId);
            for (BranchInventory bi : branchInventories) {
                if (bi.getProduct() != null) {
                    stockMap.put(bi.getProduct().getId(), bi.getAvailableQuantity());
                }
            }
        }

        // Fetch all active products
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .sorted(Comparator.comparing(Product::getName))
                .toList();

        final Long currentBranchId = branchId;
        List<PosProductDto> posProducts = products.stream().map(p -> {
            int stock = stockMap.getOrDefault(p.getId(), 0);
            return PosProductDto.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .sku(p.getSku())
                    .price(p.getPrice())
                    .compareAtPrice(p.getCompareAtPrice())
                    .imageUrl(p.getPrimaryImageUrl())
                    .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                    .categoryName(p.getCategory() != null ? p.getCategory().getName() : "")
                    .availableStock(stock)
                    .build();
        }).collect(Collectors.toList());

        model.addAttribute("currentBranch", currentBranch);
        model.addAttribute("branchId", branchId);
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            model.addAttribute("branches", branchService.getActiveBranches().stream()
                    .filter(b -> b.getId().equals(principal.getBranchId())).toList());
        } else {
            model.addAttribute("branches", branchService.getActiveBranches());
        }
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("posProducts", posProducts);
        model.addAttribute("posForm", new PosOrderForm());

        return "admin/pos/index";
    }

    @PostMapping("/checkout")
    public String checkout(
            @ModelAttribute PosOrderForm form,
            RedirectAttributes redirectAttributes) {

        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);
        Long branchId = form.getBranchId();

        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            branchId = principal.getBranchId();
        }

        if (branchId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn chi nhánh thực hiện giao dịch");
            return "redirect:/admin/pos";
        }

        try {
            String staffName = principal != null ? principal.getFullName() : "Nhân viên OneShop";
            String staffEmail = principal != null ? principal.getUsername() : "sales@oneshop.vn";

            Order order = posService.createPosOrder(form, branchId, staffName, staffEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Thanh toán thành công đơn hàng #" + order.getOrderCode() + "!");
            return "redirect:/admin/pos/receipt/" + order.getId();
        } catch (Exception ex) {
            log.error("POS Checkout error: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể hoàn tất đơn hàng: " + ex.getMessage());
            return "redirect:/admin/pos" + (branchId != null ? "?branchId=" + branchId : "");
        }
    }

    @GetMapping("/receipt/{id}")
    public String receipt(@PathVariable("id") Long id, Model model) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng ID: " + id));

        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            if (!order.getBranch().getId().equals(principal.getBranchId())) {
                throw new AccessDeniedException("Bạn không có quyền xem hóa đơn của chi nhánh khác");
            }
        }

        Optional<Payment> payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(id);

        String cashier = order.getCashierName();
        if (cashier == null || cashier.isBlank()) {
            cashier = (principal != null) ? principal.getFullName() : "Nhân viên thu ngân OneShop";
        }

        model.addAttribute("order", order);
        model.addAttribute("payment", payment.orElse(null));
        model.addAttribute("cashierName", cashier);

        return "admin/pos/receipt";
    }

    @GetMapping("/customer-lookup")
    @ResponseBody
    public Map<String, Object> lookupCustomer(@RequestParam("phone") String phone) {
        Map<String, Object> res = new HashMap<>();
        if (phone == null || phone.isBlank()) {
            res.put("found", false);
            return res;
        }
        String cleanPhone = phone.replaceAll("[^0-9+]", "");
        Optional<com.oneshop.user.User> userOpt = userRepository.findFirstByPhone(cleanPhone);
        if (userOpt.isEmpty() && cleanPhone.startsWith("0")) {
            userOpt = userRepository.findFirstByPhone("+84" + cleanPhone.substring(1));
        }
        if (userOpt.isPresent()) {
            res.put("found", true);
            res.put("fullName", userOpt.get().getFullName());
            res.put("email", userOpt.get().getEmail());
        } else {
            res.put("found", false);
        }
        return res;
    }
}
