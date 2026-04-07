package com.winistore.win.service;

import com.winistore.win.dto.order.CreateOrderRequest;
import com.winistore.win.dto.order.CreateOrderResponse;
import com.winistore.win.dto.payment.VnpayCreatePaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class VnpayService {
    private static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long PENDING_TTL_SECONDS = 20 * 60;

    private final OrderPlacementService orderPlacementService;

    @Value("${payment.vnpay.tmn-code:}")
    private String tmnCode;

    @Value("${payment.vnpay.hash-secret:}")
    private String hashSecret;

    @Value("${payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    @Value("${payment.vnpay.return-url:http://localhost:8080/api/public/payments/vnpay/return}")
    private String returnUrl;

    private final Map<String, PendingCheckout> pendingByTxnRef = new ConcurrentHashMap<>();

    public VnpayService(OrderPlacementService orderPlacementService) {
        this.orderPlacementService = orderPlacementService;
    }

    public VnpayCreatePaymentResponse createPaymentUrl(CreateOrderRequest request, String clientIp) {
        validateConfig();
        cleanupExpired();

        var quote = orderPlacementService.estimateTotal(request);
        long amount = quote.totalPrice().multiply(java.math.BigDecimal.valueOf(100)).longValue();
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền thanh toán không hợp lệ");
        }

        String txnRef = buildTxnRef();
        pendingByTxnRef.put(txnRef, new PendingCheckout(request, System.currentTimeMillis(), null));

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime expire = now.plusMinutes(15);

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", tmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(amount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", txnRef);
        vnpParams.put("vnp_OrderInfo", "WinIStore thanh toan don hang " + txnRef);
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", returnUrl);
        vnpParams.put("vnp_IpAddr", (clientIp == null || clientIp.isBlank()) ? "127.0.0.1" : clientIp);
        vnpParams.put("vnp_CreateDate", now.format(VNP_TIME));
        vnpParams.put("vnp_ExpireDate", expire.format(VNP_TIME));
        String bankCode = trimToNull(request.bankCode());
        if (bankCode != null && bankCode.matches("^[A-Za-z0-9_]{2,20}$")) {
            vnpParams.put("vnp_BankCode", bankCode.toUpperCase());
        }

        String hashData = buildQuery(vnpParams, false);
        String secureHash = hmacSHA512(hashSecret, hashData);
        vnpParams.put("vnp_SecureHash", secureHash);
        String query = buildQuery(vnpParams, true);
        return new VnpayCreatePaymentResponse(payUrl + "?" + query, txnRef);
    }

    public boolean isValidSignature(Map<String, String> input) {
        validateConfig();
        String receivedHash = input.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) return false;

        Map<String, String> copied = new HashMap<>(input);
        copied.remove("vnp_SecureHash");
        copied.remove("vnp_SecureHashType");
        String hashData = buildQuery(copied, false);
        String expected = hmacSHA512(hashSecret, hashData);
        return expected.equalsIgnoreCase(receivedHash);
    }

    public Long processSuccessAndCreateOrder(String txnRef) {
        cleanupExpired();
        PendingCheckout pending = pendingByTxnRef.get(txnRef);
        if (pending == null) {
            throw new IllegalArgumentException("Phiên thanh toán đã hết hạn hoặc không tồn tại");
        }
        synchronized (pending) {
            if (pending.orderId != null) {
                return pending.orderId;
            }
            CreateOrderResponse created = orderPlacementService.placeOrder(pending.request);
            pending.orderId = created.orderId();
            return pending.orderId;
        }
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        pendingByTxnRef.entrySet().removeIf(e -> now - e.getValue().createdAtMs > PENDING_TTL_SECONDS * 1000);
    }

    private void validateConfig() {
        if (tmnCode == null || tmnCode.isBlank() || hashSecret == null || hashSecret.isBlank()) {
            throw new IllegalStateException("Thiếu cấu hình VNPay (tmn-code/hash-secret)");
        }
    }

    private String buildTxnRef() {
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "WIN" + shortId;
    }

    private String buildQuery(Map<String, String> params, boolean encodeValue) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> encode(e.getKey()) + "=" + (encodeValue ? encode(e.getValue()) : encode(e.getValue())))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Không thể tạo checksum VNPay", e);
        }
    }

    private static class PendingCheckout {
        private final CreateOrderRequest request;
        private final long createdAtMs;
        private Long orderId;

        private PendingCheckout(CreateOrderRequest request, long createdAtMs, Long orderId) {
            this.request = request;
            this.createdAtMs = createdAtMs;
            this.orderId = orderId;
        }
    }
}

