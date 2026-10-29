package com.oneshop.catalog;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false, columnDefinition = "NVARCHAR(200)")
    private String name;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(name = "short_description", columnDefinition = "NVARCHAR(500)")
    private String shortDescription;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "ingredients", columnDefinition = "NVARCHAR(MAX)")
    private String ingredients;

    @Column(name = "how_to_use", columnDefinition = "NVARCHAR(MAX)")
    private String howToUse;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 18, scale = 2)
    private BigDecimal compareAtPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Builder.Default
    @Column(name = "is_featured", nullable = false)
    private Boolean isFeatured = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductImage> images = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getPrimaryImageUrl() {
        if (images != null && !images.isEmpty()) {
            for (ProductImage img : images) {
                if (Boolean.TRUE.equals(img.getIsPrimary()) && img.getSecureUrl() != null && !img.getSecureUrl().isBlank()) {
                    String url = img.getSecureUrl();
                    if (url.contains("photo-1556228722-d0b71f3b3924")) {
                        return "https://images.unsplash.com/photo-1556228720-195a672e8a03?w=800&q=80";
                    }
                    return url;
                }
            }
            if (images.get(0).getSecureUrl() != null && !images.get(0).getSecureUrl().isBlank()) {
                String url = images.get(0).getSecureUrl();
                if (url.contains("photo-1556228722-d0b71f3b3924")) {
                    return "https://images.unsplash.com/photo-1556228720-195a672e8a03?w=800&q=80";
                }
                return url;
            }
        }
        return "https://images.unsplash.com/photo-1556228720-195a672e8a03?w=800&q=80";
    }

    public boolean hasDiscount() {
        return compareAtPrice != null && compareAtPrice.compareTo(price) > 0;
    }

    public int getDiscountPercentage() {
        if (!hasDiscount()) return 0;
        BigDecimal diff = compareAtPrice.subtract(price);
        return diff.multiply(BigDecimal.valueOf(100)).divide(compareAtPrice, 0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
