package com.winistore.win.repository;

import com.winistore.win.model.entity.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "orderDetails", "orderDetails.product"})
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}
