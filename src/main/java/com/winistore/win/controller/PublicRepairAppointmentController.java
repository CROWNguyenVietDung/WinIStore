package com.winistore.win.controller;

import com.winistore.win.dto.repair.RepairAppointmentDto;
import com.winistore.win.dto.repair.RepairCancelRequest;
import com.winistore.win.model.entity.RepairAppointment;
import com.winistore.win.model.entity.RepairAppointmentImage;
import com.winistore.win.model.entity.User;
import com.winistore.win.model.enums.RepairAppointmentStatus;
import com.winistore.win.repository.RepairAppointmentRepository;
import com.winistore.win.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/repair-appointments")
public class PublicRepairAppointmentController {

    private static final int MAX_REPAIR_IMAGES = 10;

    private final UserRepository userRepository;
    private final RepairAppointmentRepository repairAppointmentRepository;

    public PublicRepairAppointmentController(
            UserRepository userRepository,
            RepairAppointmentRepository repairAppointmentRepository
    ) {
        this.userRepository = userRepository;
        this.repairAppointmentRepository = repairAppointmentRepository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public RepairAppointmentDto create(
            @RequestParam("userId") Long userId,
            @RequestParam("deviceName") String deviceName,
            @RequestParam("issueDescription") String issueDescription,
            @RequestParam("appointmentDate") String appointmentDateRaw,
            @RequestParam(value = "images", required = false) List<MultipartFile> images
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        LocalDate appointmentDate;
        try {
            appointmentDate = LocalDate.parse(appointmentDateRaw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Ngày hẹn không hợp lệ (yyyy-MM-dd).");
        }
        if (appointmentDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày hẹn phải là hôm nay hoặc ngày sau.");
        }

        String device = trimOrThrow(deviceName, "Tên thiết bị");
        String issue = trimOrThrow(issueDescription, "Mô tả lỗi");

        List<RepairAppointmentImage> imageEntities = new ArrayList<>();
        List<MultipartFile> fileList = images == null ? List.of() : images;
        int count = 0;
        for (MultipartFile file : fileList) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (count >= MAX_REPAIR_IMAGES) {
                throw new IllegalArgumentException("Tối đa " + MAX_REPAIR_IMAGES + " ảnh.");
            }
            String url = saveRepairImage(file);
            imageEntities.add(
                    RepairAppointmentImage.builder()
                            .imageUrl(url)
                            .sortOrder(count)
                            .build()
            );
            count++;
        }

        RepairAppointment appointment = RepairAppointment.builder()
                .user(user)
                .deviceName(device)
                .issueDescription(issue)
                .appointmentDate(appointmentDate)
                .status(RepairAppointmentStatus.PENDING)
                .actualCost(null)
                .build();

        for (RepairAppointmentImage img : imageEntities) {
            img.setRepairAppointment(appointment);
            appointment.getImages().add(img);
        }

        RepairAppointment saved = repairAppointmentRepository.save(appointment);
        return toDto(saved);
    }

    @GetMapping(value = "/by-user/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public List<RepairAppointmentDto> listByUser(@PathVariable Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Không tìm thấy người dùng.");
        }
        return repairAppointmentRepository.findByUser_IdOrderByAppointmentDateDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping(value = "/{id}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public RepairAppointmentDto cancel(@PathVariable Long id, @Valid @RequestBody RepairCancelRequest req) {
        RepairAppointment r = repairAppointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn."));
        if (!r.getUser().getId().equals(req.userId())) {
            throw new IllegalArgumentException("Bạn không có quyền hủy lịch này.");
        }
        if (r.getStatus() != RepairAppointmentStatus.PENDING) {
            throw new IllegalArgumentException("Chỉ có thể hủy khi lịch đang chờ xác nhận.");
        }
        r.setStatus(RepairAppointmentStatus.CANCELLED);
        return toDto(r);
    }

    private static String trimOrThrow(String raw, String fieldLabel) {
        if (raw == null) {
            throw new IllegalArgumentException(fieldLabel + " không được để trống.");
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException(fieldLabel + " không được để trống.");
        }
        return t;
    }

    private String saveRepairImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("File không phải ảnh hợp lệ.");
        }

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        if (!ext.matches("\\.(png|jpg|jpeg|gif|webp)")) {
            ext = ".png";
        }

        String filename = UUID.randomUUID() + ext;
        Path dir = Paths.get(System.getProperty("user.dir"), "uploads", "repair-appointments");

        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/repair-appointments/" + filename;
        } catch (IOException e) {
            throw new IllegalArgumentException("Không thể lưu ảnh. Vui lòng thử lại.");
        }
    }

    private RepairAppointmentDto toDto(RepairAppointment r) {
        List<String> urls = r.getImages() == null
                ? List.of()
                : r.getImages().stream().map(RepairAppointmentImage::getImageUrl).toList();
        return new RepairAppointmentDto(
                r.getId(),
                r.getDeviceName(),
                r.getIssueDescription(),
                r.getAppointmentDate(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getActualCost(),
                urls
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
