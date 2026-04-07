package com.winistore.win.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record ProductCardDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer discountPercent,
        Integer stockQuantity,
        Integer soldQuantity,
        @JsonProperty("imageUrl") String image,
        Long categoryId,
        String categoryName,
        String categoryType
) {
}

