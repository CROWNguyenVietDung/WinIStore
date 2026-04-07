package com.winistore.win.dto.product;

import java.time.LocalDateTime;

public record ProductReviewDto(
        Long id,
        Long productId,
        Long userId,
        String userName,
        Integer rating,
        String comment,
        LocalDateTime createdAt
) {
}
