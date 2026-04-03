package com.winistore.win.dto.order;

import java.util.List;

public record CreateOrderRequest(
        Long userId,
        List<CreateOrderItem> items,
        String paymentMethod,
        String recipientName,
        String recipientPhone,
        String shippingAddress
) {
    public record CreateOrderItem(
            Long productId,
            Integer quantity
    ) {
    }
}

