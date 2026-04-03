package com.winistore.win.dto.admin;

import java.math.BigDecimal;

public record AdminProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Integer discountPercent,
        Integer stockQuantity,
        String imageUrl,
        Long categoryId,
        String categoryName,
        String categoryType,
        String description,
        Boolean visibleForUser
) {
}

