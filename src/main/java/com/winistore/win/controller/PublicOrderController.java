package com.winistore.win.controller;

import com.winistore.win.dto.order.CreateOrderRequest;
import com.winistore.win.dto.order.CreateOrderResponse;
import com.winistore.win.dto.order.OrderSummaryDto;
import com.winistore.win.model.entity.Order;
import com.winistore.win.repository.OrderRepository;
import com.winistore.win.service.OrderPlacementService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/orders")
public class PublicOrderController {
    private final OrderRepository orderRepository;
    private final OrderPlacementService orderPlacementService;

    public PublicOrderController(
            OrderRepository orderRepository,
            OrderPlacementService orderPlacementService
    ) {
        this.orderRepository = orderRepository;
        this.orderPlacementService = orderPlacementService;
    }

    @GetMapping("/by-user/{userId}")
    @Transactional(readOnly = true)
    public List<OrderSummaryDto> listForUser(@PathVariable Long userId) {
        if (userId == null) {
            return List.of();
        }
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public CreateOrderResponse create(@RequestBody CreateOrderRequest req) {
        return orderPlacementService.placeOrder(req);
    }

    private OrderSummaryDto toSummary(Order o) {
        String recipientName = trimToNull(o.getUser() != null ? o.getUser().getFullName() : null);
        if (recipientName == null) {
            recipientName = trimToNull(o.getUser() != null ? o.getUser().getEmail() : null);
        }
        if (recipientName == null) {
            recipientName = "Khách hàng WinIStore";
        }

        String recipientPhone = trimToNull(o.getUser() != null ? o.getUser().getPhone() : null);
        if (recipientPhone == null) {
            recipientPhone = "Chưa cập nhật";
        }

        String shippingAddress = "Theo thông tin nhận hàng đã xác nhận";

        int itemCount = o.getOrderDetails() == null ? 0 : o.getOrderDetails().stream()
                .mapToInt(d -> {
                    Integer qty = d.getQuantity();
                    return qty == null ? 0 : qty;
                })
                .sum();
        List<OrderSummaryDto.OrderItemDto> items = o.getOrderDetails() == null
                ? List.of()
                : o.getOrderDetails().stream()
                .map(d -> new OrderSummaryDto.OrderItemDto(
                        d.getProduct() != null ? d.getProduct().getId() : null,
                        d.getProduct() != null ? d.getProduct().getName() : "Sản phẩm",
                        d.getQuantity()
                ))
                .toList();
        return new OrderSummaryDto(
                o.getId(),
                o.getStatus() == null ? null : o.getStatus().name(),
                o.getCreatedAt(),
                o.getTotalPrice(),
                null,
                null,
                recipientName,
                recipientPhone,
                shippingAddress,
                itemCount,
                items,
                trimToNull(o.getCustomerNote())
        );
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
