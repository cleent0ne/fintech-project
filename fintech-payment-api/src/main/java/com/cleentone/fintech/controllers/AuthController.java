package com.cleentone.fintech.controllers;

import com.cleentone.fintech.config.JwtUtil;
import com.cleentone.fintech.dto.AuthResponse;
import com.cleentone.fintech.dto.LoginRequest;
import com.cleentone.fintech.dto.RegisterRequest;
import com.cleentone.fintech.dto.UserResponse;
import com.cleentone.fintech.exception.ResourceNotFoundException;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.services.AuthService;
import com.cleentone.fintech.services.TokenBlacklistService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
        // 201 Created — not 200 OK — because a new resource was created
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // Blacklist the token's jti so it cannot be reused
            String jti = jwtUtil.extractJti(token);
            tokenBlacklistService.blacklist(jti, jwtUtil.extractExpiration(token));
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(UserResponse.from(user));
    }
}