package com.winistore.win.controller;

import com.winistore.win.dto.product.ProductReviewCreateRequest;
import com.winistore.win.dto.product.ProductReviewDto;
import com.winistore.win.model.entity.Product;
import com.winistore.win.model.entity.ProductReview;
import com.winistore.win.model.entity.User;
import com.winistore.win.repository.ProductRepository;
import com.winistore.win.repository.ProductReviewRepository;
import com.winistore.win.repository.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/public/products")
public class PublicProductReviewController {
    private static final int MAX_COMMENT_LENGTH = 2000;

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductReviewRepository productReviewRepository;

    public PublicProductReviewController(
            ProductRepository productRepository,
            UserRepository userRepository,
            ProductReviewRepository productReviewRepository
    ) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.productReviewRepository = productReviewRepository;
    }

    @GetMapping(value = "/{productId}/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public List<ProductReviewDto> list(@PathVariable Long productId) {
        return productReviewRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping(value = "/review-summary", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> summary(@RequestParam(name = "ids") List<Long> ids) {
        Set<Long> unique = ids == null ? Set.of() : ids.stream().filter(x -> x != null && x > 0).collect(java.util.stream.Collectors.toSet());
        if (unique.isEmpty()) return List.of();
        return unique.stream().map(pid -> {
            List<ProductReview> list = productReviewRepository.findByProductIdOrderByCreatedAtDesc(pid);
            int count = list.size();
            double avg = count == 0
                    ? 5.0
                    : list.stream()
                    .map(r -> java.util.Objects.requireNonNullElse(r.getRating(), 5))
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(5.0);
            return Map.<String, Object>of(
                    "productId", pid,
                    "avgRating", Math.round(avg * 10.0) / 10.0,
                    "reviewCount", count
            );
        }).toList();
    }

    @PostMapping(value = "/{productId}/reviews", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ProductReviewDto create(@PathVariable Long productId, @RequestBody ProductReviewCreateRequest req) {
        if (req == null || req.userId() == null) {
            throw new IllegalArgumentException("Thiếu userId.");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        int rating = java.util.Objects.requireNonNullElse(req.rating(), 0);
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Số sao phải từ 1 đến 5.");
        }
        String comment = sanitizeComment(req.comment());
        ProductReview review = new ProductReview();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(rating);
        review.setComment(comment);
        review.setCreatedAt(LocalDateTime.now());
        review.setOneStarRead(rating != 1);

        return toDto(productReviewRepository.save(review));
    }

    @DeleteMapping(value = "/{productId}/reviews/{reviewId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Boolean>> delete(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @RequestParam Long userId
    ) {
        ProductReview review = productReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá."));
        if (review.getProduct() == null || !productId.equals(review.getProduct().getId())) {
            throw new IllegalArgumentException("Đánh giá không thuộc sản phẩm này.");
        }
        if (review.getUser() == null || !userId.equals(review.getUser().getId())) {
            throw new IllegalArgumentException("Bạn không có quyền xóa đánh giá này.");
        }
        productReviewRepository.delete(review);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private String sanitizeComment(String raw) {
        if (raw == null) throw new IllegalArgumentException("Vui lòng nhập bình luận.");
        String t = raw.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập bình luận.");
        if (t.length() > MAX_COMMENT_LENGTH) {
            t = t.substring(0, MAX_COMMENT_LENGTH);
        }
        return t;
    }

    private ProductReviewDto toDto(ProductReview r) {
        return new ProductReviewDto(
                r.getId(),
                r.getProduct() != null ? r.getProduct().getId() : null,
                r.getUser() != null ? r.getUser().getId() : null,
                r.getUser() != null ? r.getUser().getFullName() : null,
                r.getRating(),
                r.getComment(),
                r.getCreatedAt()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
