package com.winistore.win.dto.auth;

public record AuthResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatar,
        String role
) {
}

