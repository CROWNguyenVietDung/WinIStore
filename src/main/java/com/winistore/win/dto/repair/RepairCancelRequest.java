package com.winistore.win.dto.repair;

import jakarta.validation.constraints.NotNull;

public record RepairCancelRequest(
        @NotNull Long userId
) {
}
