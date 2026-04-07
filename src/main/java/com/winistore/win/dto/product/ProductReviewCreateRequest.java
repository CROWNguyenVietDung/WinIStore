package com.winistore.win.dto.product;

public record ProductReviewCreateRequest(
        Long userId,
        Integer rating,
        String comment
) {
}
