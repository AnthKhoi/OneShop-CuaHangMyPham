package com.oneshop.branch;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/branches")
@RequiredArgsConstructor
public class AdminBranchController {

    private final BranchService branchService;

    @GetMapping
    public String listBranches(Model model) {
        model.addAttribute("branches", branchService.getAllBranches());
        model.addAttribute("newBranch", new Branch());
        return "admin/branches/list";
    }

    @PostMapping("/save")
    public String saveBranch(
            @ModelAttribute("newBranch") Branch branch,
            RedirectAttributes redirectAttributes) {

        try {
            branchService.saveBranch(branch);
            redirectAttributes.addFlashAttribute("successMessage", "Lưu chi nhánh '" + branch.getName() + "' thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/branches";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            Branch b = branchService.toggleBranchStatus(id);
            String status = Boolean.TRUE.equals(b.getActive()) ? "MỞ CỬA" : "ĐÓNG CỬA";
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái chi nhánh '" + b.getName() + "' thành " + status);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/branches";
    }

    @GetMapping("/{id}/staff")
    public String manageStaff(@PathVariable("id") Long id, Model model) {
        Branch branch = branchService.getBranchById(id);
        model.addAttribute("branch", branch);
        model.addAttribute("staffList", branchService.getBranchStaff(id));
        model.addAttribute("unassignedStaff", branchService.getUnassignedStaff());
        return "admin/branches/staff";
    }

    @PostMapping("/{id}/staff/assign")
    public String assignStaff(
            @PathVariable("id") Long branchId,
            @RequestParam("userId") Long userId,
            RedirectAttributes redirectAttributes) {
        try {
            branchService.assignStaffToBranch(userId, branchId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phân công nhân viên vào chi nhánh thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/branches/" + branchId + "/staff";
    }

    @PostMapping("/{id}/staff/remove")
    public String removeStaff(
            @PathVariable("id") Long branchId,
            @RequestParam("userId") Long userId,
            RedirectAttributes redirectAttributes) {
        try {
            branchService.removeStaffFromBranch(userId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã rút nhân viên khỏi chi nhánh!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/branches/" + branchId + "/staff";
    }

    @GetMapping("/api/availability/{productId}")
    @ResponseBody
    public ResponseEntity<List<BranchStockDto>> checkAvailability(@PathVariable("productId") Long productId) {
        return ResponseEntity.ok(branchService.checkProductStockAcrossBranches(productId));
    }
}
