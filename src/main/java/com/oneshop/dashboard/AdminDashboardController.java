package com.oneshop.dashboard;

import com.oneshop.branch.BranchService;
import com.oneshop.security.SecurityUtils;
import com.oneshop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final BranchService branchService;

    @GetMapping
    public String dashboard(
            @RequestParam(value = "branchId", required = false) Long branchId,
            Model model) {

        UserPrincipal principal = SecurityUtils.getCurrentUser().orElse(null);

        // Sales staff do not have dashboard analytics access -> redirect directly to order operations
        if (principal != null && principal.hasRole("ROLE_SALES_STAFF")) {
            return "redirect:/admin/orders";
        }

        // Branch Manager can only see their own assigned branch
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            branchId = principal.getBranchId();
        }

        DashboardStatsDto stats = dashboardService.getDashboardStats(branchId);

        model.addAttribute("stats", stats);
        model.addAttribute("selectedBranchId", branchId);
        if (principal != null && !principal.isAdmin() && principal.getBranchId() != null) {
            model.addAttribute("branches", branchService.getActiveBranches().stream()
                    .filter(b -> b.getId().equals(principal.getBranchId()))
                    .toList());
        } else {
            model.addAttribute("branches", branchService.getActiveBranches());
        }

        return "admin/dashboard";
    }
}
