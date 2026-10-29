package com.oneshop.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController {

    private final CatalogService catalogService;
    private final com.oneshop.storage.CloudinaryService cloudinaryService;

    @GetMapping
    public String listBrands(
            @RequestParam(value = "edit", required = false) Long editId,
            Model model) {
        if (com.oneshop.security.SecurityUtils.getCurrentUser().filter(u -> u.hasRole("ROLE_SALES_STAFF")).isPresent()) {
            return "redirect:/admin/orders";
        }
        model.addAttribute("brands", catalogService.getAllBrands());
        if (editId != null) {
            try {
                model.addAttribute("editBrand", catalogService.getBrandById(editId));
            } catch (Exception ignored) {
                model.addAttribute("editBrand", null);
            }
        }
        model.addAttribute("newBrand", new Brand());
        return "admin/brands/list";
    }

    @PostMapping("/save")
    public String saveBrand(
            @ModelAttribute("newBrand") Brand brand,
            @RequestParam(value = "logoFile", required = false) org.springframework.web.multipart.MultipartFile logoFile,
            RedirectAttributes redirectAttributes) {

        try {
            boolean isEdit = brand.getId() != null;
            if (logoFile != null && !logoFile.isEmpty()) {
                var uploadResult = cloudinaryService.uploadImage(logoFile, "brands");
                brand.setLogoUrl(uploadResult.secureUrl());
            } else if (isEdit) {
                Brand existing = catalogService.getBrandById(brand.getId());
                if (brand.getLogoUrl() == null || brand.getLogoUrl().isBlank()) {
                    brand.setLogoUrl(existing.getLogoUrl());
                }
            }
            catalogService.saveBrand(brand);
            redirectAttributes.addFlashAttribute("successMessage",
                    (isEdit ? "Cập nhật" : "Thêm mới") + " thương hiệu '" + brand.getName() + "' thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/brands";
    }

    @PostMapping("/{id}/delete")
    public String deleteBrand(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            catalogService.deleteBrand(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa thương hiệu thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/brands";
    }
}
