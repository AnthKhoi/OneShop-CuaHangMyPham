package com.oneshop.catalog;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants", indexes = {
        @Index(name = "idx_variant_sku", columnList = "sku", unique = true),
        @Index(name = "idx_variant_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 60)
    private String sku;

    @Column(name = "variant_name", nullable = false, length = 120)
    private String variantName; // Ví dụ: "Đỏ tương ớt (Chili)", "50ml", "100ml"

    @Column(name = "color_code", length = 30)
    private String colorCode; // Ví dụ: "#c43235"

    @Column(length = 50)
    private String volume; // Ví dụ: "50ml", "3.5g"

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 18, scale = 2)
    private BigDecimal compareAtPrice;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
