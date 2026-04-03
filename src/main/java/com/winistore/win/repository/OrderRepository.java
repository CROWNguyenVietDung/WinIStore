package com.winistore.win.repository;

import com.winistore.win.model.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}
