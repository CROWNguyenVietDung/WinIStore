package com.winistore.win.dto.payment;

public record VnpayCreatePaymentResponse(
        String paymentUrl,
        String txnRef
) {
}

