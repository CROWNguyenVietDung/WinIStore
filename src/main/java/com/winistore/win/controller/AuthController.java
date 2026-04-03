package com.winistore.win.controller;

import com.winistore.win.dto.auth.AuthResponse;
import com.winistore.win.dto.auth.LoginRequest;
import com.winistore.win.dto.auth.RegisterRequest;
import com.winistore.win.dto.auth.UpdateAvatarRequest;
import com.winistore.win.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/avatar")
    public ResponseEntity<AuthResponse> updateAvatar(@Valid @RequestBody UpdateAvatarRequest req) {
        return ResponseEntity.ok(authService.updateAvatar(req.email(), req.avatar()));
    }

    @PostMapping("/avatar/upload")
    public ResponseEntity<AuthResponse> uploadAvatar(
            @RequestParam("email") String email,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(authService.updateAvatarUpload(email, file));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}

