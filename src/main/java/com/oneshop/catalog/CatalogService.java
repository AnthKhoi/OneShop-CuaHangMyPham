package com.oneshop.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    public static String toSlug(String input) {
        if (input == null) return "";
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(normalized).replaceAll("");
        slug = NONLATIN.matcher(slug).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH).replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    // Categories
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public List<Category> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    public List<Category> getRootCategories() {
        return categoryRepository.findByParentCategoryIsNullAndActiveTrueOrderBySortOrderAsc();
    }

    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục: " + slug));
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy danh mục ID: " + id));
    }

    @Transactional
    public Category saveCategory(Category category) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên danh mục không được để trống");
        }
        category.setName(category.getName().trim());
        if (category.getSlug() == null || category.getSlug().trim().isEmpty()) {
            category.setSlug(toSlug(category.getName()));
        } else {
            category.setSlug(toSlug(category.getSlug()));
        }

        if (category.getId() != null) {
            Category existing = getCategoryById(category.getId());
            existing.setName(category.getName());
            existing.setSlug(category.getSlug());
            existing.setDescription(category.getDescription());
            existing.setImageUrl(category.getImageUrl());
            if (category.getActive() != null) {
                existing.setActive(category.getActive());
            }
            if (category.getSortOrder() != null) {
                existing.setSortOrder(category.getSortOrder());
            }
            return categoryRepository.save(existing);
        }

        if (category.getActive() == null) {
            category.setActive(true);
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = getCategoryById(id);
        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new IllegalStateException("Không thể xóa danh mục '" + category.getName() + "' vì đang có " + productCount + " sản phẩm thuộc danh mục này. Vui lòng chuyển hoặc xóa sản phẩm trước!");
        }
        long subCount = categoryRepository.countByParentCategoryId(id);
        if (subCount > 0) {
            throw new IllegalStateException("Không thể xóa danh mục '" + category.getName() + "' vì đang có " + subCount + " danh mục con trực thuộc!");
        }
        categoryRepository.delete(category);
    }

    // Brands
    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    public List<Brand> getActiveBrands() {
        return brandRepository.findByActiveTrueOrderByNameAsc();
    }

    public Brand getBrandBySlug(String slug) {
        return brandRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thương hiệu: " + slug));
    }

    public Brand getBrandById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thương hiệu ID: " + id));
    }

    @Transactional
    public Brand saveBrand(Brand brand) {
        if (brand.getName() == null || brand.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên thương hiệu không được để trống");
        }
        brand.setName(brand.getName().trim());
        if (brand.getSlug() == null || brand.getSlug().trim().isEmpty()) {
            brand.setSlug(toSlug(brand.getName()));
        } else {
            brand.setSlug(toSlug(brand.getSlug()));
        }

        if (brand.getId() != null) {
            Brand existing = getBrandById(brand.getId());
            existing.setName(brand.getName());
            existing.setSlug(brand.getSlug());
            existing.setDescription(brand.getDescription());
            existing.setLogoUrl(brand.getLogoUrl());
            if (brand.getActive() != null) {
                existing.setActive(brand.getActive());
            }
            return brandRepository.save(existing);
        }

        if (brand.getActive() == null) {
            brand.setActive(true);
        }
        return brandRepository.save(brand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        Brand brand = getBrandById(id);
        long productCount = productRepository.countByBrandId(id);
        if (productCount > 0) {
            throw new IllegalStateException("Không thể xóa thương hiệu '" + brand.getName() + "' vì đang có " + productCount + " sản phẩm thuộc thương hiệu này. Vui lòng cập nhật sản phẩm trước!");
        }
        brandRepository.delete(brand);
    }

    // Products
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getFeaturedProducts() {
        return productRepository.findByIsFeaturedTrueAndActiveTrue();
    }

    public List<Product> getNewArrivals(int limit) {
        return productRepository.findTopNewArrivals(PageRequest.of(0, limit));
    }

    public List<Product> getRelatedProducts(Long categoryId, Long excludeId, int limit) {
        return productRepository.findRelatedProducts(categoryId, excludeId, PageRequest.of(0, limit));
    }

    public Product getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với slug: " + slug));
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + id));
    }

    public Page<Product> searchProducts(String keyword, Long categoryId, Long brandId, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        return productRepository.searchCatalog(keyword, categoryId, brandId, minPrice, maxPrice, pageable);
    }

    @Transactional
    public Product saveProduct(Product product) {
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Giá sản phẩm phải lớn hơn hoặc bằng 0");
        }

        // Attach managed Category
        if (product.getCategory() != null && product.getCategory().getId() != null) {
            Category category = categoryRepository.findById(product.getCategory().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Danh mục được chọn không tồn tại"));
            product.setCategory(category);
        } else {
            throw new IllegalArgumentException("Vui lòng chọn danh mục cho sản phẩm");
        }

        // Attach managed Brand
        if (product.getBrand() != null && product.getBrand().getId() != null) {
            Brand brand = brandRepository.findById(product.getBrand().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Thương hiệu được chọn không tồn tại"));
            product.setBrand(brand);
        } else {
            throw new IllegalArgumentException("Vui lòng chọn thương hiệu cho sản phẩm");
        }

        // Validate SKU uniqueness
        if (product.getSku() != null) {
            product.setSku(product.getSku().trim().toUpperCase());
            java.util.Optional<Product> existingWithSku = productRepository.findBySku(product.getSku());
            if (existingWithSku.isPresent() && (product.getId() == null || !existingWithSku.get().getId().equals(product.getId()))) {
                throw new IllegalArgumentException("Mã SKU '" + product.getSku() + "' đã được sử dụng bởi sản phẩm khác. Vui lòng nhập mã SKU khác.");
            }
        }

        // Generate unique slug
        if (product.getSlug() == null || product.getSlug().trim().isEmpty()) {
            String baseSlug = toSlug(product.getName());
            if (baseSlug.isEmpty()) {
                baseSlug = "san-pham";
            }
            String uniqueSlug = baseSlug;
            int counter = 1;
            while (productRepository.findBySlug(uniqueSlug)
                    .filter(existing -> !existing.getId().equals(product.getId()))
                    .isPresent()) {
                uniqueSlug = baseSlug + "-" + counter;
                counter++;
            }
            product.setSlug(uniqueSlug);
        }

        return productRepository.save(product);
    }

    @Transactional
    public void softDeleteProduct(Long id) {
        Product product = getProductById(id);
        product.setActive(false);
        productRepository.save(product);
    }

    // Images
    public List<ProductImage> getProductImages(Long productId) {
        return imageRepository.findByProductIdOrderBySortOrderAsc(productId);
    }

    @Transactional
    public void deleteProductImage(Long productId, Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hình ảnh ID: " + imageId));
        if (!image.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Hình ảnh không thuộc sản phẩm này");
        }
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        imageRepository.delete(image);

        if (wasPrimary) {
            List<ProductImage> remaining = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
            if (!remaining.isEmpty()) {
                ProductImage nextPrimary = remaining.get(0);
                nextPrimary.setIsPrimary(true);
                imageRepository.save(nextPrimary);
            }
        }
    }

    @Transactional
    public void setProductPrimaryImage(Long productId, Long imageId) {
        List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
        for (ProductImage img : images) {
            img.setIsPrimary(img.getId().equals(imageId));
        }
        imageRepository.saveAll(images);
    }
}
