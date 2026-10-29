package com.oneshop.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);
    boolean existsBySlug(String slug);

    long countByCategoryId(Long categoryId);
    long countByBrandId(Long brandId);

    List<Product> findByIsFeaturedTrueAndActiveTrue();

    @Query("SELECT p FROM Product p WHERE p.active = true ORDER BY p.createdAt DESC")
    List<Product> findTopNewArrivals(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.category.id = :categoryId AND p.id <> :excludeId")
    List<Product> findRelatedProducts(@Param("categoryId") Long categoryId, @Param("excludeId") Long excludeId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId OR p.category.parentCategory.id = :categoryId) " +
           "AND (:brandId IS NULL OR p.brand.id = :brandId) " +
           "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(p.category.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(p.brand.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     (p.shortDescription IS NOT NULL AND LOWER(p.shortDescription) LIKE LOWER(CONCAT('%', :keyword, '%'))))")
    Page<Product> searchCatalog(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);
}
