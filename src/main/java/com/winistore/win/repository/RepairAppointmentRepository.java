package com.winistore.win.repository;

import com.winistore.win.model.entity.RepairAppointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepairAppointmentRepository extends JpaRepository<RepairAppointment, Long> {
    List<RepairAppointment> findByUserId(Long userId);
}
