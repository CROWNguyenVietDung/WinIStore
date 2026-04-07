package com.winistore.win.controller;

import com.winistore.win.dto.admin.AdminProductReviewDto;
import com.winistore.win.model.entity.ProductReview;
import com.winistore.win.repository.ProductReviewRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminProductReviewController {
    private final ProductReviewRepository productReviewRepository;

    public AdminProductReviewController(ProductReviewRepository productReviewRepository) {
        this.productReviewRepository = productReviewRepository;
    }

    @GetMapping(value = "/products/{productId}/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public List<AdminProductReviewDto> listByProduct(@PathVariable Long productId) {
        return productReviewRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping(value = "/reviews/one-star-alerts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public List<AdminProductReviewDto> oneStarAlerts() {
        return productReviewRepository.findByRatingAndOneStarReadOrderByCreatedAtDesc(1, false).stream()
                .map(this::toDto)
                .toList();
    }

    @PatchMapping(value = "/reviews/{id}/mark-read", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public AdminProductReviewDto markRead(@PathVariable Long id) {
        ProductReview r = productReviewRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá."));
        r.setOneStarRead(true);
        return toDto(productReviewRepository.save(r));
    }

    private AdminProductReviewDto toDto(ProductReview r) {
        return new AdminProductReviewDto(
                r.getId(),
                r.getProduct() != null ? r.getProduct().getId() : null,
                r.getProduct() != null ? r.getProduct().getName() : null,
                r.getUser() != null ? r.getUser().getId() : null,
                r.getUser() != null ? r.getUser().getFullName() : null,
                r.getRating(),
                r.getComment(),
                r.getCreatedAt(),
                r.getOneStarRead()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
