package com.winistore.win.dto.order;

public record CustomerCancelOrderRequest(
        Long userId,
        String reason
) {
}
