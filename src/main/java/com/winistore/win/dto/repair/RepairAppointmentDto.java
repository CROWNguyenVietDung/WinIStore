package com.winistore.win.dto.repair;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RepairAppointmentDto(
        Long id,
        String deviceName,
        String issueDescription,
        LocalDate appointmentDate,
        String status,
        BigDecimal actualCost,
        List<String> imageUrls
) {
}
