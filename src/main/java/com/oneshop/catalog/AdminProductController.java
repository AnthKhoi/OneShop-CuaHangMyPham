package com.oneshop.catalog;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchService;
import com.oneshop.inventory.InventoryService;
import com.oneshop.storage.CloudinaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@Slf4j
public class AdminProductController {

    private final CatalogService catalogService;
    private final CloudinaryService cloudinaryService;
    private final ProductImageRepository imageRepository;
    private final BranchService branchService;
    private final InventoryService inventoryService;

    @GetMapping
    public String listProducts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) Long categoryId,
            @RequestParam(value = "brand", required = false) Long brandId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        Page<Product> productPage = catalogService.searchProducts(
                keyword, categoryId, brandId, null, null,
                PageRequest.of(page, 15, Sort.by("id").descending())
        );

        model.addAttribute("productPage", productPage);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("categories", catalogService.getActiveCategories());
        model.addAttribute("brands", catalogService.getActiveBrands());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategory", categoryId);
        model.addAttribute("selectedBrand", brandId);

        return "admin/products/list";
    }

    @GetMapping("/new")
    public String newProductForm(Model model) {
        if (com.oneshop.security.SecurityUtils.getCurrentUser().filter(u -> u.hasRole("ROLE_SALES_STAFF")).isPresent()) {
            return "redirect:/admin/products";
        }
        model.addAttribute("product", new Product());
        model.addAttribute("categories", catalogService.getActiveCategories());
        model.addAttribute("brands", catalogService.getActiveBrands());
        model.addAttribute("branches", branchService.getActiveBranches());
        return "admin/products/form";
    }

    @PostMapping("/save")
    public String saveProduct(
            @Valid @ModelAttribute("product") Product product,
            BindingResult bindingResult,
            @RequestParam(value = "imageFiles", required = false) List<MultipartFile> imageFiles,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "imageUrlsInput", required = false) String imageUrlsInput,
            @RequestParam(value = "imageUrlInput", required = false) String imageUrlInput,
            @RequestParam(value = "initialQuantity", required = false) Integer initialQuantity,
            @RequestParam(value = "targetBranchId", required = false) Long targetBranchId,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (com.oneshop.security.SecurityUtils.getCurrentUser().filter(u -> u.hasRole("ROLE_SALES_STAFF")).isPresent()) {
            throw new org.springframework.security.access.AccessDeniedException("Nhân viên bán hàng không có quyền chỉnh sửa sản phẩm");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", catalogService.getActiveCategories());
            model.addAttribute("brands", catalogService.getActiveBrands());
            model.addAttribute("branches", branchService.getActiveBranches());
            if (product.getId() != null) {
                model.addAttribute("productImages", catalogService.getProductImages(product.getId()));
                model.addAttribute("branchStocks", inventoryService.getProductStockAcrossBranches(product.getId()));
            }
            return "admin/products/form";
        }

        try {
            boolean isNew = product.getId() == null;
            Product savedProduct = catalogService.saveProduct(product);

            // Handle image uploads (multiple files) and URLs
            List<MultipartFile> filesToUpload = new java.util.ArrayList<>();
            if (imageFiles != null) {
                for (MultipartFile f : imageFiles) {
                    if (f != null && !f.isEmpty()) {
                        filesToUpload.add(f);
                    }
                }
            }
            if (imageFile != null && !imageFile.isEmpty()) {
                filesToUpload.add(imageFile);
            }

            List<String> urlsToAdd = new java.util.ArrayList<>();
            if (StringUtils.hasText(imageUrlsInput)) {
                String[] split = imageUrlsInput.split("[,;\\r?\\n]+");
                for (String u : split) {
                    if (StringUtils.hasText(u)) {
                        urlsToAdd.add(u.trim());
                    }
                }
            }
            if (StringUtils.hasText(imageUrlInput)) {
                String trimmed = imageUrlInput.trim();
                if (!urlsToAdd.contains(trimmed)) {
                    urlsToAdd.add(trimmed);
                }
            }

            List<ProductImage> currentImages = imageRepository.findByProductIdOrderBySortOrderAsc(savedProduct.getId());
            boolean hasPrimary = currentImages.stream().anyMatch(img -> Boolean.TRUE.equals(img.getIsPrimary()));
            int currentMaxSort = currentImages.stream()
                    .mapToInt(img -> img.getSortOrder() != null ? img.getSortOrder() : 0)
                    .max()
                    .orElse(0);

            // Upload files
            for (MultipartFile file : filesToUpload) {
                try {
                    CloudinaryService.UploadResult uploadResult = cloudinaryService.uploadImage(file, "products");
                    boolean makePrimary = !hasPrimary;
                    currentMaxSort++;
                    ProductImage pi = ProductImage.builder()
                            .product(savedProduct)
                            .secureUrl(uploadResult.secureUrl())
                            .cloudinaryPublicId(uploadResult.publicId())
                            .altText(savedProduct.getName())
                            .isPrimary(makePrimary)
                            .sortOrder(currentMaxSort)
                            .build();
                    imageRepository.save(pi);
                    if (makePrimary) {
                        hasPrimary = true;
                    }
                } catch (Exception uploadEx) {
                    log.error("Lỗi khi tải ảnh lên Cloudinary: ", uploadEx);
                }
            }

            // Save URLs
            for (String rawUrl : urlsToAdd) {
                if (StringUtils.hasText(rawUrl)) {
                    boolean makePrimary = !hasPrimary;
                    currentMaxSort++;
                    ProductImage pi = ProductImage.builder()
                            .product(savedProduct)
                            .secureUrl(rawUrl)
                            .altText(savedProduct.getName())
                            .isPrimary(makePrimary)
                            .sortOrder(currentMaxSort)
                            .build();
                    imageRepository.save(pi);
                    if (makePrimary) {
                        hasPrimary = true;
                    }
                }
            }

            // If creating new product without any image provided, assign a default valid cosmetic placeholder
            if (isNew && filesToUpload.isEmpty() && urlsToAdd.isEmpty() && currentImages.isEmpty()) {
                ProductImage defaultImage = ProductImage.builder()
                        .product(savedProduct)
                        .secureUrl("https://images.unsplash.com/photo-1556228720-195a672e8a03?w=800&q=80")
                        .altText(savedProduct.getName())
                        .isPrimary(true)
                        .sortOrder(0)
                        .build();
                imageRepository.save(defaultImage);
            }

            // Handle inventory stock initialization when creating product
            if (isNew) {
                int stockQty = (initialQuantity != null && initialQuantity > 0) ? initialQuantity : 50;
                if (targetBranchId != null) {
                    inventoryService.adjustStock(targetBranchId, savedProduct.getId(), stockQty,
                            "Khởi tạo tồn kho ban đầu khi tạo sản phẩm", "Admin");
                } else {
                    List<Branch> branches = branchService.getActiveBranches();
                    for (Branch b : branches) {
                        inventoryService.adjustStock(b.getId(), savedProduct.getId(), stockQty,
                                "Khởi tạo tồn kho ban đầu khi tạo sản phẩm", "Admin");
                    }
                }
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    (isNew ? "Thêm mới" : "Cập nhật") + " sản phẩm '" + savedProduct.getName() + "' thành công!");
            return "redirect:/admin/products";
        } catch (Exception ex) {
            log.error("Error saving product: ", ex);
            model.addAttribute("categories", catalogService.getActiveCategories());
            model.addAttribute("brands", catalogService.getActiveBrands());
            model.addAttribute("branches", branchService.getActiveBranches());
            if (product.getId() != null) {
                model.addAttribute("productImages", catalogService.getProductImages(product.getId()));
                model.addAttribute("branchStocks", inventoryService.getProductStockAcrossBranches(product.getId()));
            }
            model.addAttribute("errorMessage", ex.getMessage());
            return "admin/products/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editProductForm(@PathVariable("id") Long id, Model model) {
        if (com.oneshop.security.SecurityUtils.getCurrentUser().filter(u -> u.hasRole("ROLE_SALES_STAFF")).isPresent()) {
            return "redirect:/admin/products";
        }
        Product product = catalogService.getProductById(id);
        model.addAttribute("product", product);
        model.addAttribute("productImages", catalogService.getProductImages(id));
        model.addAttribute("categories", catalogService.getActiveCategories());
        model.addAttribute("brands", catalogService.getActiveBrands());
        model.addAttribute("branches", branchService.getActiveBranches());
        model.addAttribute("branchStocks", inventoryService.getProductStockAcrossBranches(id));
        return "admin/products/form";
    }

    @PostMapping("/{productId}/images/{imageId}/delete")
    public String deleteImage(
            @PathVariable("productId") Long productId,
            @PathVariable("imageId") Long imageId,
            RedirectAttributes redirectAttributes) {
        try {
            catalogService.deleteProductImage(productId, imageId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa ảnh thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit";
    }

    @PostMapping("/{productId}/images/{imageId}/set-primary")
    public String setPrimaryImage(
            @PathVariable("productId") Long productId,
            @PathVariable("imageId") Long imageId,
            RedirectAttributes redirectAttributes) {
        try {
            catalogService.setProductPrimaryImage(productId, imageId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã đặt làm ảnh đại diện chính!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products/" + productId + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String deleteProduct(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            catalogService.softDeleteProduct(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ngừng kinh doanh sản phẩm ID #" + id);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products";
    }
}
