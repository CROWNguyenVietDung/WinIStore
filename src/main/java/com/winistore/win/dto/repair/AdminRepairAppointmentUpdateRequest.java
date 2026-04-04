package com.winistore.win.dto.repair;

import java.math.BigDecimal;

public record AdminRepairAppointmentUpdateRequest(
        String status,
        BigDecimal actualCost
) {
}
