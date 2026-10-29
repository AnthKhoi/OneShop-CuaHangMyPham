package com.oneshop.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;

    @GetMapping
    public String listUsers(Model model) {
        var allUsers = userRepository.findAll();

        var staffUsers = allUsers.stream()
                .filter(u -> u.getRoles().stream().anyMatch(r ->
                        r.getName() == RoleName.ROLE_ADMIN ||
                        r.getName() == RoleName.ROLE_BRANCH_MANAGER ||
                        r.getName() == RoleName.ROLE_SALES_STAFF))
                .toList();

        var customerUsers = allUsers.stream()
                .filter(u -> u.getRoles().stream().allMatch(r -> r.getName() == RoleName.ROLE_CUSTOMER))
                .toList();

        model.addAttribute("users", allUsers);
        model.addAttribute("staffUsers", staffUsers);
        model.addAttribute("customerUsers", customerUsers);
        model.addAttribute("totalCount", allUsers.size());
        model.addAttribute("staffCount", staffUsers.size());
        model.addAttribute("customerCount", customerUsers.size());

        return "admin/users/list";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        user.setActive(!Boolean.TRUE.equals(user.getActive()));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("successMessage",
                "Đã " + (user.getActive() ? "kích hoạt" : "khóa") + " tài khoản " + user.getEmail());
        return "redirect:/admin/users";
    }
}
