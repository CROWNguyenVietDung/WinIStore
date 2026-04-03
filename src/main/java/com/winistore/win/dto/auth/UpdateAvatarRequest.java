package com.winistore.win.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAvatarRequest(
        @Email @NotNull String email,
        @Size(max = 500) String avatar
) {
}

