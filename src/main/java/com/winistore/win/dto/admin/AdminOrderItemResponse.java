package com.winistore.win.dto.admin;

import java.math.BigDecimal;

public record AdminOrderItemResponse(
        Long productId,
        String productName,
        String categoryName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
