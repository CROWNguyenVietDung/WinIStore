package com.winistore.win.service;

import com.winistore.win.dto.order.CreateOrderRequest;
import com.winistore.win.dto.order.CreateOrderResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrderPlacementService {
    private static final BigDecimal SHIPPING_STANDARD = new BigDecimal("30000");
    private static final int MAX_CUSTOMER_NOTE = 500;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    public OrderPlacementService(
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

    public record OrderQuote(BigDecimal goodsTotal, BigDecimal shippingFee, BigDecimal totalPrice) {
    }

    public OrderQuote estimateTotal(CreateOrderRequest req) {
        if (req == null || req.userId() == null) {
            throw new IllegalArgumentException("Thiếu userId");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng trống");
        }
        PaymentMethod paymentMethod = parsePaymentMethod(req.paymentMethod());
        BigDecimal shippingFee = shippingFor(paymentMethod);
        validateAddress(req, paymentMethod);

        Map<Long, Integer> quantities = collectQuantities(req.items());
        List<Product> products = productRepository.findAllById(quantities.keySet());
        if (products.size() != quantities.size()) {
            throw new IllegalArgumentException("Có sản phẩm không tồn tại");
        }

        BigDecimal goodsTotal = BigDecimal.ZERO;
        for (Product p : products) {
            Integer qVal = quantities.get(p.getId());
            int q = qVal == null ? 0 : qVal;
            Integer stockVal = p.getStockQuantity();
            int stock = stockVal == null ? 0 : stockVal;
            if (stock < q) {
                throw new IllegalArgumentException("Sản phẩm \"" + p.getName() + "\" không đủ hàng");
            }
            BigDecimal unitPrice = discountedUnitPrice(p.getPrice(), p.getDiscountPercent());
            goodsTotal = goodsTotal.add(unitPrice.multiply(BigDecimal.valueOf(q)));
        }
        return new OrderQuote(goodsTotal, shippingFee, goodsTotal.add(shippingFee));
    }

    @Transactional
    public CreateOrderResponse placeOrder(CreateOrderRequest req) {
        OrderQuote quote = estimateTotal(req);

        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user"));

        Map<Long, Integer> quantities = collectQuantities(req.items());
        List<Product> products = productRepository.findAllById(quantities.keySet());

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .totalPrice(BigDecimal.ZERO)
                .customerNote(sanitizeCustomerNote(req.customerNote()))
                .build();
        order = orderRepository.save(order);

        for (Product p : products) {
            int q = quantities.getOrDefault(p.getId(), 0);
            Integer stockVal = p.getStockQuantity();
            int stock = stockVal == null ? 0 : stockVal;
            if (stock < q) {
                throw new IllegalArgumentException("Sản phẩm \"" + p.getName() + "\" không đủ hàng");
            }

            BigDecimal unitPrice = discountedUnitPrice(p.getPrice(), p.getDiscountPercent());
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

        order.setTotalPrice(quote.totalPrice());
        orderRepository.save(order);
        return new CreateOrderResponse(order.getId(), quote.totalPrice(), quote.shippingFee(), quote.goodsTotal());
    }

    private Map<Long, Integer> collectQuantities(List<CreateOrderRequest.CreateOrderItem> items) {
        Map<Long, Integer> quantities = new HashMap<>();
        for (CreateOrderRequest.CreateOrderItem item : items) {
            if (item == null || item.productId() == null) continue;
            Integer quantity = item.quantity();
            int q = quantity == null ? 0 : quantity;
            if (q <= 0) continue;
            quantities.merge(item.productId(), q, Integer::sum);
        }
        if (quantities.isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng trống");
        }
        return quantities;
    }

    private void validateAddress(CreateOrderRequest req, PaymentMethod paymentMethod) {
        String shippingAddress = trimToNull(req.shippingAddress());
        if (paymentMethod != PaymentMethod.STORE_PICKUP
                && (shippingAddress == null || shippingAddress.isBlank())) {
            throw new IllegalArgumentException("Vui lòng nhập địa chỉ giao hàng");
        }
    }

    private BigDecimal shippingFor(PaymentMethod paymentMethod) {
        return paymentMethod == PaymentMethod.STORE_PICKUP ? BigDecimal.ZERO : SHIPPING_STANDARD;
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
}

