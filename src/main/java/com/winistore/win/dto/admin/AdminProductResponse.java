package com.winistore.win.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record AdminProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Integer discountPercent,
        Integer stockQuantity,
        @JsonProperty("imageUrl") String image,
        Long categoryId,
        String categoryName,
        String categoryType,
        String description,
        Boolean visibleForUser
) {
}

