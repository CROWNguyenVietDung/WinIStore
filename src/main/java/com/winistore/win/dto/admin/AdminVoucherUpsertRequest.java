package com.winistore.win.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminVoucherUpsertRequest(
        String code,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minOrderValue,
        Boolean active,
        LocalDateTime startAt,
        LocalDateTime endAt,
        /** null = không giới hạn số lần áp mã */
        Integer usageLimit
) {
}
