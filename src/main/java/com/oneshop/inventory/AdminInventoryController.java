package com.oneshop.inventory;

import com.oneshop.branch.BranchService;
import com.oneshop.catalog.CatalogService;
import com.oneshop.security.SecurityUtils;
import com.oneshop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
@Slf4j
public class AdminInventoryController {

    private final InventoryService inventoryService;
    private final BranchService branchService;
    private final CatalogService catalogService;

    @GetMapping
    public String listInventory(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model) {

        try {
            branchId = SecurityUtils.enforceBranchAccess(branchId);
            List<BranchInventory> inventoryList = inventoryService.getInventoryList(branchId, keyword);

            model.addAttribute("inventoryList", inventoryList != null ? inventoryList : java.util.Collections.emptyList());
            model.addAttribute("branches", branchService.getActiveBranches());
            model.addAttribute("products", catalogService.getAllProducts());
            model.addAttribute("selectedBranchId", branchId);
            model.addAttribute("keyword", keyword);
        } catch (Exception ex) {
            log.error("Error loading inventory list: ", ex);
            model.addAttribute("inventoryList", java.util.Collections.emptyList());
            model.addAttribute("branches", branchService.getActiveBranches());
            model.addAttribute("products", java.util.Collections.emptyList());
            model.addAttribute("errorMessage", "Không thể tải dữ liệu tồn kho: " + ex.getMessage());
        }

        return "admin/inventory/list";
    }

    @PostMapping("/adjust")
    public String adjustStock(
            @RequestParam("branchId") Long branchId,
            @RequestParam("productId") Long productId,
            @RequestParam("newQuantity") int newQuantity,
            @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {

        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);
        if (principal != null && principal.hasRole("ROLE_SALES_STAFF")) {
            throw new AccessDeniedException("Nhân viên bán hàng chỉ có quyền tra cứu tồn kho, không được điều chỉnh kho");
        }
        SecurityUtils.enforceBranchAccess(branchId);

        try {
            String actor = principal != null ? principal.getFullName() : "Admin";
            inventoryService.adjustStock(branchId, productId, newQuantity, reason, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Điều chỉnh tồn kho thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi điều chỉnh: " + ex.getMessage());
        }

        return "redirect:/admin/inventory?branchId=" + branchId;
    }

    @PostMapping("/import")
    public String importStock(
            @RequestParam("branchId") Long branchId,
            @RequestParam("productId") Long productId,
            @RequestParam("quantity") int quantity,
            @RequestParam(value = "reason", required = false) String reason,
            RedirectAttributes redirectAttributes) {

        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);
        if (principal != null && principal.hasRole("ROLE_SALES_STAFF")) {
            throw new AccessDeniedException("Nhân viên bán hàng chỉ có quyền tra cứu tồn kho, không được nhập kho");
        }
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            if (!principal.getBranchId().equals(branchId)) {
                throw new AccessDeniedException("Bạn chỉ có quyền nhập kho cho chi nhánh của mình");
            }
        }

        try {
            String actor = principal != null ? principal.getFullName() : "Admin";
            inventoryService.importStock(branchId, productId, quantity, reason, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Nhập kho thành công (" + quantity + " sản phẩm)!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi nhập kho: " + ex.getMessage());
        }

        return "redirect:/admin/inventory?branchId=" + branchId;
    }

    @PostMapping("/export")
    public String exportStock(
            @RequestParam("branchId") Long branchId,
            @RequestParam("productId") Long productId,
            @RequestParam("quantity") int quantity,
            @RequestParam(value = "reason", required = false) String reason,
            RedirectAttributes redirectAttributes) {

        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);
        if (principal != null && principal.hasRole("ROLE_SALES_STAFF")) {
            throw new AccessDeniedException("Nhân viên bán hàng chỉ có quyền tra cứu tồn kho, không được xuất kho");
        }
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            if (!principal.getBranchId().equals(branchId)) {
                throw new AccessDeniedException("Bạn chỉ có quyền xuất kho cho chi nhánh của mình");
            }
        }

        try {
            String actor = principal != null ? principal.getFullName() : "Admin";
            inventoryService.exportStock(branchId, productId, quantity, reason, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Xuất kho thành công (" + quantity + " sản phẩm)!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi xuất kho: " + ex.getMessage());
        }

        return "redirect:/admin/inventory?branchId=" + branchId;
    }

    @GetMapping("/movements")
    public String stockMovements(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "movementType", required = false) MovementType movementType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);
        if (principal != null && principal.hasRole("ROLE_SALES_STAFF")) {
            return "redirect:/admin/inventory";
        }
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            branchId = principal.getBranchId();
        }

        Page<StockMovement> movementPage = inventoryService.getMovements(
                branchId, movementType, PageRequest.of(page, 20)
        );

        model.addAttribute("movementPage", movementPage);
        model.addAttribute("movements", movementPage.getContent());
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            model.addAttribute("branches", branchService.getActiveBranches().stream()
                    .filter(b -> b.getId().equals(principal.getBranchId())).toList());
        } else {
            model.addAttribute("branches", branchService.getActiveBranches());
        }
        model.addAttribute("movementTypes", MovementType.values());
        model.addAttribute("selectedBranchId", branchId);
        model.addAttribute("selectedType", movementType);

        return "admin/inventory/movements";
    }

    @GetMapping("/low-stock")
    public String lowStockReport(
            @RequestParam(value = "branchId", required = false) Long branchId,
            Model model) {

        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);
        if (principal != null && principal.hasRole("ROLE_SALES_STAFF")) {
            return "redirect:/admin/inventory";
        }
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            branchId = principal.getBranchId();
        }

        List<BranchInventory> lowStockItems = inventoryService.getLowStockAlerts(branchId);

        model.addAttribute("lowStockItems", lowStockItems);
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            model.addAttribute("branches", branchService.getActiveBranches().stream()
                    .filter(b -> b.getId().equals(principal.getBranchId())).toList());
        } else {
            model.addAttribute("branches", branchService.getActiveBranches());
        }
        model.addAttribute("selectedBranchId", branchId);

        return "admin/inventory/low-stock";
    }
}
