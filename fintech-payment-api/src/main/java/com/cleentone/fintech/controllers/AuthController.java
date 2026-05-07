package com.cleentone.fintech.controllers;

import com.cleentone.fintech.dto.AuthResponse;
import com.cleentone.fintech.dto.LoginRequest;
import com.cleentone.fintech.dto.RegisterRequest;
import com.cleentone.fintech.dto.UserResponse;
import com.cleentone.fintech.exception.ResourceNotFoundException;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.services.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
  
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

    
     @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                        return ResponseEntity.ok(UserResponse.from(user));
    }

    // For testing
    @GetMapping("/test")
    public String test() {
        return "Hello, World!";
    }
    
}