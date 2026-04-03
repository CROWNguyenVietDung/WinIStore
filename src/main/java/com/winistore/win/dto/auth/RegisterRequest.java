package com.winistore.win.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 6, max = 255) String password,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String phone
) {
}
