package com.winistore.win.dto.admin;

import java.time.LocalDateTime;

public record AdminProductReviewDto(
        Long id,
        Long productId,
        String productName,
        Long userId,
        String userName,
        Integer rating,
        String comment,
        LocalDateTime createdAt,
        Boolean oneStarRead
) {
}
