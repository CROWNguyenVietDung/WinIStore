package com.winistore.win.dto.order;

import java.util.List;

public record CreateOrderRequest(
        Long userId,
        List<CreateOrderItem> items,
        String paymentMethod,
        String bankCode,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        String customerNote,
        String voucherCode
) {
    public record CreateOrderItem(
            Long productId,
            Integer quantity
    ) {
    }
}

