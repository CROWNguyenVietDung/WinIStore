package com.winistore.win.model.enums;

public enum RepairAppointmentStatus {
    PENDING,
    /** Admin đã xác nhận lịch; khách có thể mang máy đến trong ngày hẹn. */
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
