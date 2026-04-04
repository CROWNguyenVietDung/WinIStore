package com.winistore.win.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserAddressDto(
        Long id,
        String recipientName,
        String recipientPhone,
        @JsonProperty("detail")
        String addressLine,
        @JsonProperty("isDefault")
        Boolean isDefault
) {
}
