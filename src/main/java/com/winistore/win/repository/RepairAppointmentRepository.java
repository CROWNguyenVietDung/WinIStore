package com.winistore.win.repository;

import com.winistore.win.model.entity.RepairAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RepairAppointmentRepository extends JpaRepository<RepairAppointment, Long> {

    @Query("SELECT DISTINCT r FROM RepairAppointment r LEFT JOIN FETCH r.images WHERE r.user.id = :userId ORDER BY r.appointmentDate DESC")
    List<RepairAppointment> findByUser_IdOrderByAppointmentDateDesc(@Param("userId") Long userId);

    @Query("SELECT DISTINCT r FROM RepairAppointment r JOIN FETCH r.user u LEFT JOIN FETCH r.images ORDER BY r.appointmentDate ASC")
    List<RepairAppointment> findAllWithUserOrderByAppointmentDateAsc();
}
