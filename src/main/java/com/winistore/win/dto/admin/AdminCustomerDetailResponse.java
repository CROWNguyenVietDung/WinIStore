package com.winistore.win.dto.admin;

import java.time.LocalDate;
import java.util.List;

public record AdminCustomerDetailResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        String avatar,
        LocalDate dateOfBirth,
        List<AdminCustomerAddressResponse> addresses
) {
}
