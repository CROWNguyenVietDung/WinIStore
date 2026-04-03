package com.winistore.win.repository;

import com.winistore.win.model.entity.DeletedCustomerHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface DeletedCustomerHistoryRepository extends JpaRepository<DeletedCustomerHistory, Long> {
    List<DeletedCustomerHistory> findAllByOrderByDeletedAtDesc();
    void deleteByExpiresAtBefore(LocalDateTime time);
}
