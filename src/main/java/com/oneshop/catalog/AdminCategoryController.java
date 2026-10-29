package com.oneshop.catalog;

import com.oneshop.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CatalogService catalogService;
    private final com.oneshop.storage.CloudinaryService cloudinaryService;

    @GetMapping
    public String listCategories(
            @RequestParam(value = "edit", required = false) Long editId,
            Model model) {
        if (SecurityUtils.getCurrentUser().filter(u -> u.hasRole("ROLE_SALES_STAFF")).isPresent()) {
            return "redirect:/admin/orders";
        }
        model.addAttribute("categories", catalogService.getAllCategories());
        if (editId != null) {
            try {
                model.addAttribute("editCategory", catalogService.getCategoryById(editId));
            } catch (Exception ignored) {
                model.addAttribute("editCategory", null);
            }
        }
        model.addAttribute("newCategory", new Category());
        return "admin/categories/list";
    }

    @PostMapping("/save")
    public String saveCategory(
            @ModelAttribute("newCategory") Category category,
            @RequestParam(value = "imageFile", required = false) org.springframework.web.multipart.MultipartFile imageFile,
            RedirectAttributes redirectAttributes) {

        try {
            boolean isEdit = category.getId() != null;
            if (imageFile != null && !imageFile.isEmpty()) {
                var uploadResult = cloudinaryService.uploadImage(imageFile, "categories");
                category.setImageUrl(uploadResult.secureUrl());
            } else if (isEdit) {
                Category existing = catalogService.getCategoryById(category.getId());
                if (category.getImageUrl() == null || category.getImageUrl().isBlank()) {
                    category.setImageUrl(existing.getImageUrl());
                }
            }
            catalogService.saveCategory(category);
            redirectAttributes.addFlashAttribute("successMessage",
                    (isEdit ? "Cập nhật" : "Thêm mới") + " danh mục '" + category.getName() + "' thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/delete")
    public String deleteCategory(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            catalogService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa danh mục thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/categories";
    }
}
