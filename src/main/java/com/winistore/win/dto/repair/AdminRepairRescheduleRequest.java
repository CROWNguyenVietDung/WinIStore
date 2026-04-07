package com.winistore.win.dto.repair;

import java.util.List;

public record AdminRepairRescheduleRequest(
        List<String> suggestedDates
) {
}
