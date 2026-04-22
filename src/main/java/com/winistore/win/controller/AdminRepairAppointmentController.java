package com.winistore.win.controller;

import com.winistore.win.dto.repair.AdminRepairAppointmentResponse;
import com.winistore.win.dto.repair.AdminRepairRescheduleRequest;
import com.winistore.win.dto.repair.AdminRepairAppointmentUpdateRequest;
import com.winistore.win.model.entity.RepairAppointment;
import com.winistore.win.model.entity.RepairAppointmentImage;
import com.winistore.win.model.enums.RepairAppointmentStatus;
import com.winistore.win.repository.RepairAppointmentRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/repair-appointments")
public class AdminRepairAppointmentController {

    private final RepairAppointmentRepository repairAppointmentRepository;

    public AdminRepairAppointmentController(RepairAppointmentRepository repairAppointmentRepository) {
        this.repairAppointmentRepository = repairAppointmentRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public List<AdminRepairAppointmentResponse> list(@RequestParam(required = false) String date) {
        List<RepairAppointment> all = repairAppointmentRepository.findAllWithUserOrderByAppointmentDateAsc();
        if (date == null || date.isBlank()) {
            return all.stream().map(this::toAdminDto).toList();
        }
        LocalDate day;
        try {
            day = LocalDate.parse(date.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Tham số date phải là yyyy-MM-dd.");
        }
        return all.stream()
                .filter(r -> r.getAppointmentDate() != null && r.getAppointmentDate().equals(day))
                .map(this::toAdminDto)
                .toList();
    }

    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public AdminRepairAppointmentResponse updateStatus(
            @PathVariable Long id,
            @RequestBody AdminRepairAppointmentUpdateRequest req
    ) {
        RepairAppointment r = repairAppointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn."));
        if (req.status() == null || req.status().isBlank()) {
            throw new IllegalArgumentException("Trạng thái mới là bắt buộc.");
        }
        RepairAppointmentStatus next;
        try {
            next = RepairAppointmentStatus.valueOf(req.status().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ.");
        }

        RepairAppointmentStatus cur = r.getStatus();
        switch (next) {
            case CONFIRMED -> {
                if (cur != RepairAppointmentStatus.PENDING) {
                    throw new IllegalArgumentException("Chỉ xác nhận khi lịch đang Chờ xác nhận.");
                }
                r.setStatus(RepairAppointmentStatus.CONFIRMED);
            }
            case IN_PROGRESS -> {
                if (cur != RepairAppointmentStatus.CONFIRMED) {
                    throw new IllegalArgumentException("Chỉ chuyển sang Đang sửa khi lịch đã được xác nhận (khách đã mang máy đến).");
                }
                r.setStatus(next);
            }
            case COMPLETED -> {
                if (cur != RepairAppointmentStatus.IN_PROGRESS) {
                    throw new IllegalArgumentException("Chỉ hoàn thành khi đang Đang sửa.");
                }
                if (req.actualCost() == null) {
                    throw new IllegalArgumentException("Bắt buộc nhập chi phí thực tế (actual_cost) khi hoàn thành.");
                }
                if (req.actualCost().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Chi phí thực tế không được âm.");
                }
                r.setActualCost(req.actualCost());
                r.setStatus(next);
            }
            case CANCELLED -> {
                if (cur == RepairAppointmentStatus.COMPLETED || cur == RepairAppointmentStatus.CANCELLED) {
                    throw new IllegalArgumentException("Không thể hủy lịch ở trạng thái hiện tại.");
                }
                r.setStatus(next);
            }
            case PENDING -> throw new IllegalArgumentException("Không thể đặt lại về Chờ xác nhận từ admin.");
        }

        return toAdminDto(r);
    }

    @PatchMapping(value = "/{id}/reschedule-options", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public AdminRepairAppointmentResponse suggestDates(
            @PathVariable Long id,
            @RequestBody AdminRepairRescheduleRequest req
    ) {
        RepairAppointment r = repairAppointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn."));
        if (r.getStatus() != RepairAppointmentStatus.PENDING) {
            throw new IllegalArgumentException("Chỉ có thể đề xuất ngày khác khi lịch đang Chờ xác nhận.");
        }
        List<LocalDate> dates = parseSuggestedDates(req == null ? null : req.suggestedDates());
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("Cần chọn ít nhất 1 ngày hẹn khác.");
        }
        if (dates.size() > 10) {
            throw new IllegalArgumentException("Tối đa 10 ngày đề xuất.");
        }
        r.setSuggestedDatesCsv(toCsv(dates));
        r.setStatus(RepairAppointmentStatus.PENDING);
        return toAdminDto(r);
    }

    private AdminRepairAppointmentResponse toAdminDto(RepairAppointment r) {
        var u = r.getUser();
        List<String> urls = r.getImages() == null
                ? List.of()
                : r.getImages().stream().map(RepairAppointmentImage::getImageUrl).toList();
        List<String> suggestedDates = parseSuggestedDatesCsv(r.getSuggestedDatesCsv()).stream()
                .map(LocalDate::toString)
                .toList();
        return new AdminRepairAppointmentResponse(
                r.getId(),
                u != null ? u.getId() : null,
                u != null ? u.getFullName() : null,
                u != null ? u.getEmail() : null,
                u != null ? u.getPhone() : null,
                r.getDeviceName(),
                r.getIssueDescription(),
                r.getAppointmentDate(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getActualCost(),
                urls,
                suggestedDates
        );
    }

    private List<LocalDate> parseSuggestedDates(List<String> rawDates) {
        if (rawDates == null) return List.of();
        LocalDate today = LocalDate.now();
        LinkedHashSet<LocalDate> unique = new LinkedHashSet<>();
        for (String s : rawDates) {
            if (s == null || s.isBlank()) continue;
            try {
                LocalDate d = LocalDate.parse(s.trim());
                if (d.isBefore(today)) {
                    throw new IllegalArgumentException("Ngày đề xuất phải từ hôm nay trở đi.");
                }
                unique.add(d);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Ngày đề xuất không hợp lệ (yyyy-MM-dd).");
            }
        }
        return unique.stream().sorted().toList();
    }

    private List<LocalDate> parseSuggestedDatesCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return parseSuggestedDates(List.of(csv.split(",")));
    }

    private String toCsv(List<LocalDate> dates) {
        return dates.stream().map(LocalDate::toString).reduce((a, b) -> a + "," + b).orElse(null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
