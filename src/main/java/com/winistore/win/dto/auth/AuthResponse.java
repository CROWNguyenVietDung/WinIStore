package com.winistore.win.dto.auth;

import java.util.List;

public record AuthResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatar,
        String role,
        String dateOfBirth,
        List<UserAddressDto> addresses
) {
}

