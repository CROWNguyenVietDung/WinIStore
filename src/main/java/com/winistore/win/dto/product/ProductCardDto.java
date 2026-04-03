package com.winistore.win.dto.product;

import java.math.BigDecimal;

public record ProductCardDto(
        Long id,
        String name,
        BigDecimal price,
        Integer discountPercent,
        Integer stockQuantity,
        Integer soldQuantity,
        String imageUrl,
        Long categoryId,
        String categoryName,
        String categoryType
) {
}

