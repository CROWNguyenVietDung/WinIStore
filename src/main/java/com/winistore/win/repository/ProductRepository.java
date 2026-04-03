package com.winistore.win.repository;

import com.winistore.win.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
}
