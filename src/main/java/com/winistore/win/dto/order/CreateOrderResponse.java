package com.winistore.win.dto.order;

import java.math.BigDecimal;

public record CreateOrderResponse(
        Long orderId,
        BigDecimal totalPrice,
        BigDecimal shippingFee,
        BigDecimal goodsTotal
) {
}

