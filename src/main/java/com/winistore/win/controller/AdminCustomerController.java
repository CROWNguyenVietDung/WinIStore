package com.winistore.win.controller;

import com.winistore.win.dto.admin.AdminCustomerCreateRequest;
import com.winistore.win.dto.admin.AdminCustomerDetailResponse;
import com.winistore.win.dto.admin.AdminCustomerResponse;
import com.winistore.win.dto.admin.AdminCustomerUpdateRequest;
import com.winistore.win.dto.admin.AdminCustomerAddressResponse;
import com.winistore.win.dto.admin.DeletedCustomerHistoryResponse;
import com.winistore.win.model.entity.Address;
import com.winistore.win.model.entity.DeletedCustomerHistory;
import com.winistore.win.model.entity.User;
import com.winistore.win.model.enums.Role;
import com.winistore.win.repository.AddressRepository;
import com.winistore.win.repository.DeletedCustomerHistoryRepository;
import com.winistore.win.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final DeletedCustomerHistoryRepository deletedCustomerHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminCustomerController(
            UserRepository userRepository,
            AddressRepository addressRepository,
            DeletedCustomerHistoryRepository deletedCustomerHistoryRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.deletedCustomerHistoryRepository = deletedCustomerHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<AdminCustomerResponse> listCustomers() {
        return userRepository.findAllByRole(Role.USER).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public AdminCustomerResponse create(@Valid @RequestBody AdminCustomerCreateRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new IllegalArgumentException("Email đã tồn tại");
        }
        if (userRepository.existsByPhone(req.phone())) {
            throw new IllegalArgumentException("Số điện thoại đã tồn tại");
        }

        User user = User.builder()
                .username(req.email())
                .email(req.email())
                .fullName(req.fullName())
                .phone(req.phone())
                .avatar(req.avatar())
                .dateOfBirth(req.dateOfBirth())
                .password(passwordEncoder.encode(req.password()))
                .role(Role.USER)
                .build();

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    @PutMapping("/{id}")
    public AdminCustomerResponse update(
            @PathVariable Long id,
            @Valid @RequestBody AdminCustomerUpdateRequest req
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng"));

        if (userRepository.findByEmail(req.email()).isPresent()
                && !userRepository.findByEmail(req.email()).get().getId().equals(id)) {
            throw new IllegalArgumentException("Email đã tồn tại");
        }

        if (userRepository.findByPhone(req.phone()).isPresent()
                && !userRepository.findByPhone(req.phone()).get().getId().equals(id)) {
            throw new IllegalArgumentException("Số điện thoại đã tồn tại");
        }

        user.setEmail(req.email());
        user.setUsername(req.email());
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setAvatar(req.avatar());
        user.setDateOfBirth(req.dateOfBirth());

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    @GetMapping("/{id}/details")
    public AdminCustomerDetailResponse details(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng"));

        List<AdminCustomerAddressResponse> addresses = addressRepository.findByUserId(id).stream()
                .map(this::toAddressResponse)
                .toList();

        return new AdminCustomerDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar(),
                user.getDateOfBirth(),
                addresses
        );
    }

    @GetMapping("/deletion-history")
    public List<DeletedCustomerHistoryResponse> deletionHistory() {
        deletedCustomerHistoryRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        return deletedCustomerHistoryRepository.findAllByOrderByDeletedAtDesc().stream()
                .map(this::toDeletedHistoryResponse)
                .toList();
    }

    @PostMapping("/deletion-history/{historyId}/restore")
    public AdminCustomerResponse restoreDeletedCustomer(@PathVariable Long historyId) {
        deletedCustomerHistoryRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        DeletedCustomerHistory history = deletedCustomerHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dữ liệu lịch sử xóa"));

        if (userRepository.findByEmail(history.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email đã tồn tại, không thể hoàn lại");
        }
        if (userRepository.findByPhone(history.getPhone()).isPresent()) {
            throw new IllegalArgumentException("Số điện thoại đã tồn tại, không thể hoàn lại");
        }

        User restored = User.builder()
                .username(history.getEmail())
                .email(history.getEmail())
                .fullName(history.getFullName())
                .phone(history.getPhone())
                .avatar(history.getAvatar())
                .dateOfBirth(history.getDateOfBirth())
                .password(passwordEncoder.encode("123456"))
                .role(Role.USER)
                .build();
        restored = userRepository.save(restored);

        if (history.getAddressesSnapshot() != null && !history.getAddressesSnapshot().isBlank()) {
            String[] lines = history.getAddressesSnapshot().split("\\n");
            for (String line : lines) {
                if (line == null || line.isBlank()) continue;
                String trimmed = line.trim();
                boolean isDefault = trimmed.endsWith("(mac dinh)");
                if (isDefault) {
                    trimmed = trimmed.substring(0, trimmed.length() - "(mac dinh)".length()).trim();
                }
                String[] parts = trimmed.split("\\|");
                String recipientName = parts.length > 0 ? parts[0].trim() : "-";
                String recipientPhone = parts.length > 1 ? parts[1].trim() : "-";
                String addressLine = parts.length > 2 ? parts[2].trim() : "-";

                Address address = Address.builder()
                        .user(restored)
                        .recipientName(recipientName)
                        .recipientPhone(recipientPhone)
                        .addressLine(addressLine)
                        .isDefault(isDefault)
                        .build();
                addressRepository.save(address);
            }
        }

        deletedCustomerHistoryRepository.deleteById(historyId);
        return toResponse(restored);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng"));

        List<Address> addresses = addressRepository.findByUserId(id);
        String addressesSnapshot = addresses.isEmpty()
                ? ""
                : addresses.stream()
                .map(a -> String.format(
                        "%s | %s | %s%s",
                        nullToDash(a.getRecipientName()),
                        nullToDash(a.getRecipientPhone()),
                        nullToDash(a.getAddressLine()),
                        Boolean.TRUE.equals(a.getIsDefault()) ? " (mac dinh)" : ""
                ))
                .collect(Collectors.joining("\n"));

        DeletedCustomerHistory history = new DeletedCustomerHistory();
        history.setOriginalUserId(user.getId());
        history.setFullName(user.getFullName());
        history.setEmail(user.getEmail());
        history.setPhone(user.getPhone());
        history.setAvatar(user.getAvatar());
        history.setDateOfBirth(user.getDateOfBirth());
        history.setAddressesSnapshot(addressesSnapshot);
        history.setDeletedAt(LocalDateTime.now());
        history.setExpiresAt(LocalDateTime.now().plusDays(30));
        deletedCustomerHistoryRepository.save(history);

        userRepository.deleteById(id);
    }

    private AdminCustomerResponse toResponse(User user) {
        return new AdminCustomerResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar(),
                user.getDateOfBirth()
        );
    }

    private AdminCustomerAddressResponse toAddressResponse(Address address) {
        return new AdminCustomerAddressResponse(
                address.getRecipientName(),
                address.getRecipientPhone(),
                address.getAddressLine(),
                address.getIsDefault()
        );
    }

    private DeletedCustomerHistoryResponse toDeletedHistoryResponse(DeletedCustomerHistory item) {
        return new DeletedCustomerHistoryResponse(
                item.getId(),
                item.getOriginalUserId(),
                item.getFullName(),
                item.getEmail(),
                item.getPhone(),
                item.getAvatar(),
                item.getDateOfBirth(),
                item.getAddressesSnapshot(),
                item.getDeletedAt(),
                item.getExpiresAt()
        );
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}

