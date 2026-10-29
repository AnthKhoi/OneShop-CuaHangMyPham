package com.oneshop.promotion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/promotions")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
public class AdminPromotionController {

    private final PromotionService promotionService;

    @GetMapping
    public String listPromotions(Model model) {
        if (com.oneshop.security.SecurityUtils.getCurrentUser().filter(u -> u.hasRole("ROLE_SALES_STAFF")).isPresent()) {
            return "redirect:/admin/orders";
        }
        model.addAttribute("coupons", promotionService.getAllCoupons());
        model.addAttribute("newCoupon", new Coupon());
        model.addAttribute("discountTypes", CouponDiscountType.values());
        return "admin/promotions/list";
    }

    @PostMapping("/save")
    public String saveCoupon(
            @ModelAttribute("newCoupon") Coupon coupon,
            RedirectAttributes redirectAttributes) {
        try {
            promotionService.saveCoupon(coupon);
            redirectAttributes.addFlashAttribute("successMessage", "Lưu mã giảm giá '" + coupon.getCode() + "' thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/promotions";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            Coupon c = promotionService.toggleStatus(id);
            String status = Boolean.TRUE.equals(c.getActive()) ? "KÍCH HOẠT" : "TẠM KHÓA";
            redirectAttributes.addFlashAttribute("successMessage", "Đã đổi trạng thái voucher '" + c.getCode() + "' thành " + status);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/promotions";
    }

    @PostMapping("/{id}/delete")
    public String deleteCoupon(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            promotionService.deleteCoupon(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa voucher thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/promotions";
    }
}
