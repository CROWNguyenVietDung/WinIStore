package com.winistore.win.dto.admin;

import java.util.List;

public record AdminOrderCreateRequest(
        Long userId,
        List<AdminOrderItem> items
) {
    public record AdminOrderItem(
            Long productId,
            Integer quantity
    ) {}
}
