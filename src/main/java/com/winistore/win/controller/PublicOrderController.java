package com.winistore.win.controller;

import com.winistore.win.dto.order.CreateOrderRequest;
import com.winistore.win.dto.order.CreateOrderResponse;
import com.winistore.win.dto.order.OrderSummaryDto;
import com.winistore.win.model.entity.Order;
import com.winistore.win.model.entity.OrderDetail;
import com.winistore.win.model.entity.Product;
import com.winistore.win.model.entity.User;
import com.winistore.win.model.enums.OrderStatus;
import com.winistore.win.model.enums.PaymentMethod;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/public/orders")
public class PublicOrderController {
    private static final BigDecimal SHIPPING_STANDARD = new BigDecimal("30000");

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    public PublicOrderController(
            UserRepository userRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderDetailRepository orderDetailRepository
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
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
        if (req == null || req.userId() == null) {
            throw new IllegalArgumentException("Thiếu userId");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng trống");
        }

        PaymentMethod paymentMethod = parsePaymentMethod(req.paymentMethod());
        BigDecimal shippingFee = shippingFor(paymentMethod);

        String shippingAddress = trimToNull(req.shippingAddress());

        if (paymentMethod != PaymentMethod.STORE_PICKUP
                && (shippingAddress == null || shippingAddress.isBlank())) {
            throw new IllegalArgumentException("Vui lòng nhập địa chỉ giao hàng");
        }

        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user"));

        Map<Long, Integer> quantities = new HashMap<>();
        for (CreateOrderRequest.CreateOrderItem item : req.items()) {
            if (item == null || item.productId() == null) continue;
            Integer quantity = item.quantity();
            int q = quantity == null ? 0 : quantity;
            if (q <= 0) continue;
            quantities.merge(item.productId(), q, Integer::sum);
        }
        if (quantities.isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng trống");
        }

        List<Product> products = productRepository.findAllById(quantities.keySet());
        if (products.size() != quantities.size()) {
            throw new IllegalArgumentException("Có sản phẩm không tồn tại");
        }

        BigDecimal goodsTotal = BigDecimal.ZERO;
        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .totalPrice(BigDecimal.ZERO)
                .customerNote(sanitizeCustomerNote(req.customerNote()))
                .build();
        order = orderRepository.save(order);

        for (Product p : products) {
            Integer qVal = quantities.get(p.getId());
            int q = qVal == null ? 0 : qVal;
            Integer stockVal = p.getStockQuantity();
            int stock = stockVal == null ? 0 : stockVal;
            if (stock < q) {
                throw new IllegalArgumentException("Sản phẩm \"" + p.getName() + "\" không đủ hàng");
            }

            BigDecimal unitPrice = discountedUnitPrice(p.getPrice(), p.getDiscountPercent());
            BigDecimal line = unitPrice.multiply(BigDecimal.valueOf(q));
            goodsTotal = goodsTotal.add(line);

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

        BigDecimal grandTotal = goodsTotal.add(shippingFee);
        order.setTotalPrice(grandTotal);
        orderRepository.save(order);

        return new CreateOrderResponse(order.getId(), grandTotal, shippingFee, goodsTotal);
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

    private BigDecimal shippingFor(PaymentMethod paymentMethod) {
        return paymentMethod == PaymentMethod.STORE_PICKUP
                ? BigDecimal.ZERO
                : SHIPPING_STANDARD;
    }

    private PaymentMethod parsePaymentMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Chọn phương thức thanh toán");
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return PaymentMethod.valueOf(u);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ");
        }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static final int MAX_CUSTOMER_NOTE = 500;

    private String sanitizeCustomerNote(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        if (t.length() > MAX_CUSTOMER_NOTE) {
            t = t.substring(0, MAX_CUSTOMER_NOTE);
        }
        return t;
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
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
