package com.winistore.win.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AdminCustomerUpdateRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String phone,
        @Size(max = 500) String avatar,
        LocalDate dateOfBirth
) {
}

