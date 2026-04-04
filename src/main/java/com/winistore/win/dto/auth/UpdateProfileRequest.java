package com.winistore.win.dto.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateProfileRequest(
        @NotBlank String fullName,
        @NotBlank String phone,
        String dateOfBirth,
        @NotNull @Valid List<AddressLineRequest> addresses
) {
    public record AddressLineRequest(
            @NotBlank String recipientName,
            @NotBlank String recipientPhone,
            @NotBlank String detail,
            boolean isDefault
    ) {
    }
}
