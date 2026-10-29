package com.oneshop.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    List<ProductReview> findByProductIdAndActiveTrueOrderByCreatedAtDesc(Long productId);

    Page<ProductReview> findByProductIdAndActiveTrueOrderByCreatedAtDesc(Long productId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.product.id = :productId AND r.active = true")
    Double getAverageRating(@Param("productId") Long productId);

    long countByProductIdAndActiveTrue(Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    java.util.Optional<ProductReview> findByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT r.product.id, AVG(r.rating), COUNT(r.id) FROM ProductReview r WHERE r.active = true GROUP BY r.product.id")
    List<Object[]> getRatingStatsGroupedByProduct();
}
