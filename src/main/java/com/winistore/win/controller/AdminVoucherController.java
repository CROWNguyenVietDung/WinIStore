package com.winistore.win.controller;

import com.winistore.win.dto.admin.AdminVoucherResponse;
import com.winistore.win.dto.admin.AdminVoucherToggleRequest;
import com.winistore.win.dto.admin.AdminVoucherUpsertRequest;
import com.winistore.win.model.entity.Voucher;
import com.winistore.win.model.enums.VoucherDiscountType;
import com.winistore.win.repository.VoucherRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/vouchers")
public class AdminVoucherController {
    private final VoucherRepository voucherRepository;

    public AdminVoucherController(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public List<AdminVoucherResponse> list() {
        LocalDateTime now = LocalDateTime.now();
        return voucherRepository.findAll().stream()
                .map(v -> {
                    if (v.getEndAt() != null && now.isAfter(v.getEndAt()) && Boolean.TRUE.equals(v.getActive())) {
                        v.setActive(false);
                        voucherRepository.save(v);
                    }
                    return toDto(v);
                })
                .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public AdminVoucherResponse create(@RequestBody AdminVoucherUpsertRequest req) {
        Voucher v = new Voucher();
        apply(v, req, false);
        return toDto(voucherRepository.save(v));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public AdminVoucherResponse update(@PathVariable Long id, @RequestBody AdminVoucherUpsertRequest req) {
        Voucher v = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy voucher."));
        apply(v, req, true);
        return toDto(voucherRepository.save(v));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        if (!voucherRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy voucher.");
        }
        voucherRepository.deleteById(id);
    }

    @PatchMapping(value = "/{id}/active", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public AdminVoucherResponse toggleActive(@PathVariable Long id, @RequestBody AdminVoucherToggleRequest req) {
        Voucher v = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy voucher."));
        if (v.getEndAt() != null && LocalDateTime.now().isAfter(v.getEndAt())) {
            v.setActive(false);
            return toDto(voucherRepository.save(v));
        }
        v.setActive(req != null && Boolean.TRUE.equals(req.active()));
        return toDto(voucherRepository.save(v));
    }

    private void apply(Voucher v, AdminVoucherUpsertRequest req, boolean updating) {
        if (req == null) throw new IllegalArgumentException("Thiếu dữ liệu voucher.");
        String code = req.code() == null ? "" : req.code().trim().toUpperCase();
        if (code.isEmpty()) throw new IllegalArgumentException("Mã voucher là bắt buộc.");
        VoucherDiscountType type;
        try {
            type = VoucherDiscountType.valueOf((req.discountType() == null ? "" : req.discountType().trim().toUpperCase()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Loại giảm giá không hợp lệ.");
        }
        BigDecimal value = req.discountValue() == null ? BigDecimal.ZERO : req.discountValue();
        if (value.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Giá trị giảm không được âm.");
        BigDecimal minOrder = req.minOrderValue() == null ? BigDecimal.ZERO : req.minOrderValue();
        if (minOrder.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Giá trị đơn tối thiểu không được âm.");
        if (req.startAt() != null && req.endAt() != null && req.endAt().isBefore(req.startAt())) {
            throw new IllegalArgumentException("Hạn sử dụng không hợp lệ.");
        }
        if (!updating || !code.equalsIgnoreCase(v.getCode())) {
            voucherRepository.findByCodeIgnoreCase(code).ifPresent(exist -> {
                if (v.getId() == null || !exist.getId().equals(v.getId())) {
                    throw new IllegalArgumentException("Mã voucher đã tồn tại.");
                }
            });
        }
        v.setCode(code);
        v.setDiscountType(type);
        v.setDiscountValue(value);
        v.setMinOrderValue(minOrder);
        v.setActive(req.active() == null ? Boolean.TRUE : req.active());
        v.setStartAt(req.startAt());
        v.setEndAt(req.endAt());
        if (v.getEndAt() != null && LocalDateTime.now().isAfter(v.getEndAt())) {
            v.setActive(false);
        }
    }

    private AdminVoucherResponse toDto(Voucher v) {
        String status = "INACTIVE";
        LocalDateTime now = LocalDateTime.now();
        if (v.getEndAt() != null && now.isAfter(v.getEndAt())) {
            status = "EXPIRED";
        } else if (Boolean.TRUE.equals(v.getActive())) {
            status = "ACTIVE";
        }
        return new AdminVoucherResponse(
                v.getId(),
                v.getCode(),
                v.getDiscountType() == null ? null : v.getDiscountType().name(),
                v.getDiscountValue(),
                v.getMinOrderValue(),
                v.getActive(),
                v.getStartAt(),
                v.getEndAt(),
                status
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
