package com.winistore.win.dto.repair;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminRepairAppointmentResponse(
        Long id,
        Long userId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String deviceName,
        String issueDescription,
        LocalDate appointmentDate,
        String status,
        BigDecimal actualCost,
        List<String> imageUrls,
        List<String> suggestedDates
) {
}
