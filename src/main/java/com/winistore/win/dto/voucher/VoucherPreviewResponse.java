package com.winistore.win.dto.voucher;

import java.math.BigDecimal;

public record VoucherPreviewResponse(
        String voucherCode,
        BigDecimal goodsTotal,
        BigDecimal shippingFee,
        BigDecimal discountAmount,
        BigDecimal totalPrice
) {
}
