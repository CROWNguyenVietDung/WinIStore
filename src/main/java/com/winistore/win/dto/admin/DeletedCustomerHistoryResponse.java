package com.winistore.win.dto.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DeletedCustomerHistoryResponse(
        Long id,
        Long originalUserId,
        String fullName,
        String email,
        String phone,
        String avatar,
        LocalDate dateOfBirth,
        String addressesSnapshot,
        LocalDateTime deletedAt,
        LocalDateTime expiresAt
) {
}
