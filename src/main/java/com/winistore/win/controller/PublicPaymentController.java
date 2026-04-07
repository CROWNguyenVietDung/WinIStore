package com.winistore.win.controller;

import com.winistore.win.dto.order.CreateOrderRequest;
import com.winistore.win.dto.payment.VnpayCreatePaymentResponse;
import com.winistore.win.service.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/payments/vnpay")
public class PublicPaymentController {
    private final VnpayService vnpayService;

    @Value("${payment.vnpay.frontend-return-url:/vnpay-return.html}")
    private String frontendReturnUrl;

    public PublicPaymentController(VnpayService vnpayService) {
        this.vnpayService = vnpayService;
    }

    @PostMapping("/create")
    public VnpayCreatePaymentResponse create(@RequestBody CreateOrderRequest req, HttpServletRequest servletRequest) {
        String ip = clientIp(servletRequest);
        return vnpayService.createPaymentUrl(req, ip);
    }

    @GetMapping("/return")
    public RedirectView paymentReturn(@RequestParam Map<String, String> params) {
        String redirectBase = frontendReturnUrl;
        String txnRef = params.getOrDefault("vnp_TxnRef", "");
        if (!vnpayService.isValidSignature(params)) {
            return new RedirectView(redirectBase + "?success=0&message=" + enc("Chữ ký VNPay không hợp lệ"));
        }
        String responseCode = params.getOrDefault("vnp_ResponseCode", "");
        String transactionStatus = params.getOrDefault("vnp_TransactionStatus", "");
        if (!"00".equals(responseCode) || !"00".equals(transactionStatus)) {
            return new RedirectView(redirectBase + "?success=0&txnRef=" + enc(txnRef) + "&message=" + enc("Thanh toán không thành công"));
        }

        Long orderId = vnpayService.processSuccessAndCreateOrder(txnRef);
        return new RedirectView(redirectBase
                + "?success=1"
                + "&orderId=" + orderId
                + "&txnRef=" + enc(txnRef));
    }

    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> ipn(@RequestParam Map<String, String> params) {
        Map<String, String> out = new HashMap<>();
        String txnRef = params.getOrDefault("vnp_TxnRef", "");
        if (!vnpayService.isValidSignature(params)) {
            out.put("RspCode", "97");
            out.put("Message", "Invalid signature");
            return ResponseEntity.ok(out);
        }
        String responseCode = params.getOrDefault("vnp_ResponseCode", "");
        String transactionStatus = params.getOrDefault("vnp_TransactionStatus", "");
        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            vnpayService.processSuccessAndCreateOrder(txnRef);
            out.put("RspCode", "00");
            out.put("Message", "Confirm Success");
            return ResponseEntity.ok(out);
        }
        out.put("RspCode", "00");
        out.put("Message", "Payment failed");
        return ResponseEntity.ok(out);
    }

    private String clientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> badConfig(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}

