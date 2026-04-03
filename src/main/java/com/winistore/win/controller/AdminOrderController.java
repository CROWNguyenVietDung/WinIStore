package com.winistore.win.controller;

import com.winistore.win.dto.admin.AdminOrderCreateRequest;
import com.winistore.win.dto.admin.AdminOrderItemResponse;
import com.winistore.win.dto.admin.AdminOrderResponse;
import com.winistore.win.dto.admin.AdminOrderStatusUpdateRequest;
import com.winistore.win.model.entity.Order;
import com.winistore.win.model.entity.OrderDetail;
import com.winistore.win.model.entity.Product;
import com.winistore.win.model.entity.User;
import com.winistore.win.model.enums.OrderStatus;
import com.winistore.win.repository.OrderDetailRepository;
import com.winistore.win.repository.OrderRepository;
import com.winistore.win.repository.ProductRepository;
import com.winistore.win.repository.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public AdminOrderController(
            OrderRepository orderRepository,
            OrderDetailRepository orderDetailRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminOrderResponse> listActive() {
        return orderRepository.findAll().stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.COMPLETED)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public List<AdminOrderResponse> listHistory() {
        return orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.COMPLETED)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public AdminOrderResponse create(@RequestBody AdminOrderCreateRequest req) {
        if (req == null || req.userId() == null) {
            throw new IllegalArgumentException("Thiếu userId.");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng phải có ít nhất 1 sản phẩm.");
        }

        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng."));

        Map<Long, Integer> quantities = new HashMap<>();
        for (AdminOrderCreateRequest.AdminOrderItem item : req.items()) {
            if (item == null || item.productId() == null) continue;
            Integer qVal = item.quantity();
            int q = qVal == null ? 0 : qVal;
            if (q <= 0) continue;
            quantities.merge(item.productId(), q, Integer::sum);
        }
        if (quantities.isEmpty()) {
            throw new IllegalArgumentException("Số lượng sản phẩm không hợp lệ.");
        }

        List<Product> products = productRepository.findAllById(quantities.keySet());
        if (products.size() != quantities.size()) {
            throw new IllegalArgumentException("Có sản phẩm không tồn tại.");
        }

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .totalPrice(BigDecimal.ZERO)
                .build();
        order = orderRepository.save(order);

        BigDecimal total = BigDecimal.ZERO;
        for (Product p : products) {
            int q = quantities.getOrDefault(p.getId(), 0);
            Integer stockVal = p.getStockQuantity();
            int stock = stockVal == null ? 0 : stockVal;
            if (stock < q) {
                throw new IllegalArgumentException("Sản phẩm \"" + p.getName() + "\" không đủ hàng.");
            }

            BigDecimal unitPrice = discountedUnitPrice(p.getPrice(), p.getDiscountPercent());
            BigDecimal line = unitPrice.multiply(BigDecimal.valueOf(q));
            total = total.add(line);

            p.setStockQuantity(stock - q);
            Integer soldVal = p.getSoldQuantity();
            int sold = soldVal == null ? 0 : soldVal;
            p.setSoldQuantity(sold + q);
            productRepository.save(p);

            OrderDetail detail = OrderDetail.builder()
                    .order(order)
                    .product(p)
                    .quantity(q)
                    .price(unitPrice)
                    .build();
            orderDetailRepository.save(detail);
        }

        order.setTotalPrice(total);
        orderRepository.save(order);
        return toResponse(order);
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public AdminOrderResponse cancel(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order);
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalArgumentException("Đơn đã giao thành công, không thể hủy.");
        }

        restoreInventory(order);
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        return toResponse(order);
    }

    @PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public AdminOrderResponse updateStatus(@PathVariable Long id, @RequestBody AdminOrderStatusUpdateRequest req) {
        if (req == null || req.status() == null || req.status().isBlank()) {
            throw new IllegalArgumentException("Thiếu trạng thái đơn hàng.");
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(req.status().trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Trạng thái đơn hàng không hợp lệ.");
        }

        OrderStatus current = order.getStatus();
        if (current == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Đơn đã hủy, không thể đổi trạng thái.");
        }
        if (current == OrderStatus.COMPLETED) {
            throw new IllegalArgumentException("Đơn đã hoàn thành, không thể đổi trạng thái.");
        }

        if (newStatus == OrderStatus.CANCELLED) {
            return cancel(id);
        }

        order.setStatus(newStatus);
        orderRepository.save(order);
        return toResponse(order);
    }

    private void restoreInventory(Order order) {
        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        for (OrderDetail d : details) {
            Product p = d.getProduct();
            if (p == null) continue;
            Integer qVal = d.getQuantity();
            int q = qVal == null ? 0 : qVal;
            Integer stockVal = p.getStockQuantity();
            int stock = stockVal == null ? 0 : stockVal;
            Integer soldVal = p.getSoldQuantity();
            int sold = soldVal == null ? 0 : soldVal;
            p.setStockQuantity(stock + q);
            p.setSoldQuantity(Math.max(0, sold - q));
            productRepository.save(p);
        }
    }

    private AdminOrderResponse toResponse(Order order) {
        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());

        List<AdminOrderItemResponse> items = details.stream()
                .map(d -> {
                    Product p = d.getProduct();
                    Integer qVal = d.getQuantity();
                    int q = qVal == null ? 0 : qVal;
                    BigDecimal unit = d.getPrice() == null ? BigDecimal.ZERO : d.getPrice();
                    BigDecimal line = unit.multiply(BigDecimal.valueOf(q));
                    return new AdminOrderItemResponse(
                            p != null ? p.getId() : null,
                            p != null ? p.getName() : "Sản phẩm",
                            (p != null && p.getCategory() != null) ? p.getCategory().getName() : null,
                            q,
                            unit,
                            line
                    );
                })
                .toList();

        int totalQuantity = items.stream()
                .mapToInt(i -> {
                    Integer qty = i.quantity();
                    return qty == null ? 0 : qty;
                })
                .sum();

        User u = order.getUser();
        return new AdminOrderResponse(
                order.getId(),
                u != null ? u.getId() : null,
                u != null ? u.getFullName() : null,
                u != null ? u.getEmail() : null,
                u != null ? u.getPhone() : null,
                order.getStatus() == null ? null : order.getStatus().name(),
                order.getCreatedAt(),
                order.getTotalPrice(),
                totalQuantity,
                items
        );
    }

    private BigDecimal discountedUnitPrice(BigDecimal price, Integer discountPercent) {
        if (price == null) return BigDecimal.ZERO;
        int d = discountPercent == null ? 0 : discountPercent;
        if (d <= 0) return price;
        if (d > 100) d = 100;
        BigDecimal factor = BigDecimal.valueOf(100 - d).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return price.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegal(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
