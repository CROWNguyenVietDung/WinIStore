package com.winistore.win.dto.admin;

public record AdminCustomerAddressResponse(
        String recipientName,
        String recipientPhone,
        String addressLine,
        Boolean isDefault
) {
}
