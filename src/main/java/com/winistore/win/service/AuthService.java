package com.winistore.win.service;

import com.winistore.win.dto.auth.AuthResponse;
import com.winistore.win.dto.auth.LoginRequest;
import com.winistore.win.dto.auth.RegisterRequest;
import com.winistore.win.model.entity.User;
import com.winistore.win.model.enums.Role;
import com.winistore.win.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
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

    private AuthResponse toResponse(User user) {
        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatar(),
                user.getRole().name()
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

