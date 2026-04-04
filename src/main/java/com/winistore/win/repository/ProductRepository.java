package com.winistore.win.repository;

import com.winistore.win.model.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategoryId(Long categoryId);

    void deleteAllByCategoryId(Long categoryId);

    void deleteAllByCategoryIdIn(List<Long> categoryIds);

    Page<Product> findByCategoryTypeIgnoreCaseAndPriceBetween(
            String type,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    );

    Page<Product> findByCategoryNameIgnoreCaseAndPriceBetween(
            String categoryName,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    );

    Page<Product> findByDiscountPercentGreaterThanAndPriceBetween(
            Integer discountPercent,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT p FROM Product p JOIN p.category c
            WHERE (p.visibleForUser IS NULL OR p.visibleForUser = true)
            AND (:categoryId IS NULL OR c.id = :categoryId)
            AND (
              LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
            )
            """)
    List<Product> searchVisibleByKeywordAndOptionalCategory(
            @Param("q") String q,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );
}
