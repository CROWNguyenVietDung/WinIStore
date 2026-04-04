package com.winistore.win.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderSummaryDto(
        Long id,
        String status,
        LocalDateTime createdAt,
        BigDecimal totalPrice,
        BigDecimal shippingFee,
        String paymentMethod,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        int itemCount,
        List<OrderItemDto> items,
        String customerNote
) {
    public record OrderItemDto(
            Long productId,
            String productName,
            Integer quantity
    ) {}
}
