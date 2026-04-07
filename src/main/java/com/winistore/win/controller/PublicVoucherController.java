package com.winistore.win.controller;

import com.winistore.win.dto.order.CreateOrderRequest;
import com.winistore.win.dto.voucher.VoucherPreviewResponse;
import com.winistore.win.service.OrderPlacementService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public/vouchers")
public class PublicVoucherController {
    private final OrderPlacementService orderPlacementService;

    public PublicVoucherController(OrderPlacementService orderPlacementService) {
        this.orderPlacementService = orderPlacementService;
    }

    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public VoucherPreviewResponse preview(@RequestBody CreateOrderRequest req) {
        OrderPlacementService.OrderQuote q = orderPlacementService.estimateTotal(req);
        return new VoucherPreviewResponse(q.appliedVoucherCode(), q.goodsTotal(), q.shippingFee(), q.discountAmount(), q.totalPrice());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
