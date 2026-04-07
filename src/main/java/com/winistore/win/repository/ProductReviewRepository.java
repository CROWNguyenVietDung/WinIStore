package com.winistore.win.repository;

import com.winistore.win.model.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    List<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId);
    List<ProductReview> findByRatingAndOneStarReadOrderByCreatedAtDesc(Integer rating, Boolean oneStarRead);
}
