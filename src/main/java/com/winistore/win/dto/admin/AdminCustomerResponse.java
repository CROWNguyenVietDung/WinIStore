package com.winistore.win.dto.admin;

public record AdminCustomerResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        String avatar,
        java.time.LocalDate dateOfBirth
) {
}

