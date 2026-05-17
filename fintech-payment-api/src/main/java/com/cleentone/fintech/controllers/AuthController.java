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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * This controller handles all the entry points for user authentication.
 * If you need to sign up, log in, or log out, this is the place.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Entry point for new users to create an account.
     */
    @Operation(summary = "Register a new user", description = "Creates a new user account and initializes default wallets.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User successfully registered"),
        @ApiResponse(responseCode = "400", description = "Invalid input or validation error"),
        @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        // We delegate the heavy lifting to the authService.
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates a user and gives them a JWT they can use for subsequent requests.
     */
    @Operation(summary = "Authenticate user", description = "Verifies credentials and returns a JWT token.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully authenticated"),
        @ApiResponse(responseCode = "401", description = "Invalid email or password")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Logs the user out by blacklisting their current JWT.
     */
    @Operation(summary = "Logout user", description = "Invalidates the current session token.")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        
        // We pull the token out of the header and blacklist it until it naturally expires.
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String jti = jwtUtil.extractJti(token);
            tokenBlacklistService.blacklist(jti, jwtUtil.extractExpiration(token));
        }
        
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * A handy endpoint for a logged-in user to see their own profile details.
     */
    @Operation(summary = "Get current user info", description = "Returns the details of the authenticated user.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        // We find the user based on the email provided by Spring Security's context.
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(UserResponse.from(user));
    }
}