package com.winistore.win.service;

import com.winistore.win.dto.auth.AuthResponse;
import com.winistore.win.dto.auth.LoginRequest;
import com.winistore.win.dto.auth.RegisterRequest;
import com.winistore.win.dto.auth.UpdateProfileRequest;
import com.winistore.win.dto.auth.UserAddressDto;
import com.winistore.win.model.entity.Address;
import com.winistore.win.model.entity.User;
import com.winistore.win.model.enums.Role;
import com.winistore.win.repository.AddressRepository;
import com.winistore.win.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            AddressRepository addressRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userRepository.findByUsername(req.email()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByPhone(req.phone())) {
            throw new IllegalArgumentException("Phone already exists");
        }

        User user = User.builder()
                .username(req.email())
                .email(req.email())
                .fullName(req.fullName())
                .phone(req.phone())
                .password(passwordEncoder.encode(req.password()))
                .role(Role.USER)
                .build();

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    public AuthResponse login(LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password())
            );

            User user = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));

            return toResponse(user);
        } catch (BadCredentialsException ex) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không đúng");
        } catch (AuthenticationException ex) {
            throw new IllegalArgumentException("Đăng nhập thất bại. Vui lòng thử lại");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        return toResponse(user);
    }

    @Transactional
    public AuthResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone().trim());

        if (req.dateOfBirth() != null && !req.dateOfBirth().isBlank()) {
            try {
                user.setDateOfBirth(LocalDate.parse(req.dateOfBirth()));
            } catch (Exception ex) {
                throw new IllegalArgumentException("Ngày sinh không hợp lệ (định dạng yyyy-MM-dd).");
            }
        }

        user.getAddresses().clear();
        if (req.addresses() != null) {
            for (UpdateProfileRequest.AddressLineRequest a : req.addresses()) {
                Address addr = Address.builder()
                        .user(user)
                        .recipientName(a.recipientName().trim())
                        .recipientPhone(a.recipientPhone().trim())
                        .addressLine(a.detail().trim())
                        .isDefault(a.isDefault())
                        .build();
                user.getAddresses().add(addr);
            }
        }
        normalizeDefaultFlags(user.getAddresses());
        userRepository.save(user);
        return toResponse(user);
    }

    private static void normalizeDefaultFlags(List<Address> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        long defaults = list.stream().filter(x -> Boolean.TRUE.equals(x.getIsDefault())).count();
        if (defaults != 1) {
            for (int i = 0; i < list.size(); i++) {
                list.get(i).setIsDefault(i == 0);
            }
        }
    }

    private AuthResponse toResponse(User user) {
        List<UserAddressDto> addrDtos = new ArrayList<>();
        if (user.getId() != null) {
            addrDtos = addressRepository.findByUserId(user.getId()).stream()
                    .map(a -> new UserAddressDto(
                            a.getId(),
                            a.getRecipientName(),
                            a.getRecipientPhone(),
                            a.getAddressLine(),
                            a.getIsDefault()
                    ))
                    .toList();
        }
        String dob = user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null;
        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatar(),
                user.getRole().name(),
                dob,
                addrDtos
        );
    }

    public AuthResponse updateAvatar(String email, String avatar) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));

        user.setAvatar(avatar);
        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    public AuthResponse updateAvatarUpload(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh đại diện.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File không phải ảnh hợp lệ.");
        }

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        if (!ext.matches("\\.(png|jpg|jpeg|gif|webp)")) {
            ext = ".png";
        }

        String filename = UUID.randomUUID() + ext;
        Path avatarDir = Paths.get(System.getProperty("user.dir"), "uploads", "avatars");
        try {
            Files.createDirectories(avatarDir);
            Path target = avatarDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String avatar = "/uploads/avatars/" + filename;
            return updateAvatar(email, avatar);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Không thể lưu ảnh. Vui lòng thử lại.");
        }
    }
}

