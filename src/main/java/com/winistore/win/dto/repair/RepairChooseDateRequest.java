package com.winistore.win.dto.repair;

import jakarta.validation.constraints.NotNull;

public record RepairChooseDateRequest(
        @NotNull Long userId,
        String selectedDate
) {
}
