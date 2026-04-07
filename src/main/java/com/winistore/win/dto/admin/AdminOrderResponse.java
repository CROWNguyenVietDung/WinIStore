package com.winistore.win.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderResponse(
        Long id,
        Long userId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String status,
        LocalDateTime createdAt,
        BigDecimal totalPrice,
        Integer totalQuantity,
        List<AdminOrderItemResponse> items,
        String customerNote,
        String cancelReason
) {
}
