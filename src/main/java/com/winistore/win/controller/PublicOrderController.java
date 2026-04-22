package com.winistore.win.controller;

import com.winistore.win.dto.order.CreateOrderRequest;
import com.winistore.win.dto.order.CreateOrderResponse;
import com.winistore.win.dto.order.CustomerCancelOrderRequest;
import com.winistore.win.dto.order.OrderSummaryDto;
import com.winistore.win.model.entity.Order;
import com.winistore.win.model.entity.OrderDetail;
import com.winistore.win.model.enums.OrderStatus;
import com.winistore.win.model.enums.PaymentMethod;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @PostMapping(path = "/{id}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public OrderSummaryDto cancelByCustomer(@PathVariable Long id, @RequestBody CustomerCancelOrderRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Thiếu thông tin hủy đơn hàng");
        }
        Order cancelled = orderPlacementService.cancelByCustomer(id, req.userId(), req.reason());
        return toSummary(cancelled);
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
                .map(this::toItemDto)
                .toList();
        PaymentMethod pm = o.getPaymentMethod();
        String pmName = pm == null ? null : pm.name();
        return new OrderSummaryDto(
                o.getId(),
                o.getStatus() == null ? null : o.getStatus().name(),
                o.getCreatedAt(),
                o.getTotalPrice(),
                o.getShippingFee(),
                pmName,
                paymentStatusLabel(o.getStatus(), pm),
                o.getDiscountAmount(),
                trimToNull(o.getVoucherCode()),
                recipientName,
                recipientPhone,
                shippingAddress,
                itemCount,
                items,
                trimToNull(o.getCustomerNote())
        );
    }

    private OrderSummaryDto.OrderItemDto toItemDto(OrderDetail d) {
        BigDecimal unit = d.getPrice() == null ? BigDecimal.ZERO : d.getPrice();
        Integer qVal = d.getQuantity();
        int q = qVal == null ? 0 : qVal;
        BigDecimal line = unit.multiply(BigDecimal.valueOf(q)).setScale(2, RoundingMode.HALF_UP);
        return new OrderSummaryDto.OrderItemDto(
                d.getProduct() != null ? d.getProduct().getId() : null,
                d.getProduct() != null ? d.getProduct().getName() : "Sản phẩm",
                d.getQuantity(),
                unit,
                line
        );
    }

    private String paymentStatusLabel(OrderStatus status, PaymentMethod paymentMethod) {
        if (status == OrderStatus.CANCELLED) {
            return "Đã hủy";
        }
        if (status == OrderStatus.COMPLETED) {
            return "Đã thanh toán";
        }
        if (paymentMethod == PaymentMethod.VNPAY) {
            return "Đã thanh toán";
        }
        if (paymentMethod == PaymentMethod.COD) {
            return "Chưa thanh toán (thu khi giao)";
        }
        if (paymentMethod == PaymentMethod.STORE_PICKUP) {
            return "Chưa thanh toán (tại cửa hàng)";
        }
        return "—";
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
