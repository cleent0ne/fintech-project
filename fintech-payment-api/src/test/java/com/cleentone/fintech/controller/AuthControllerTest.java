package com.cleentone.fintech.controller;

import com.cleentone.fintech.config.JwtUtil;
import com.cleentone.fintech.controllers.AuthController;
import com.cleentone.fintech.dto.*;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.services.AuthService;
import com.cleentone.fintech.services.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.checkerframework.checker.units.qual.m;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import com.cleentone.fintech.services.TokenBlacklistService;
import com.cleentone.fintech.config.RateLimitFilter;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

     @MockBean
    private JwtUtil jwtUtil; 

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private RateLimitFilter rateLimitFilter;


    @Test
    void registerUser_Success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("SecurePass1!");
        request.setFullName("Test User");

        AuthResponse response = AuthResponse.builder()
                .email(request.getEmail())
                .token("mock-jwt-token")
                .message("Registration successful")
                .expiresIn(86400L)
                .build();

        Mockito.when(authService.register(Mockito.any(RegisterRequest.class))).thenReturn(response);
        mockMvc.perform(post("/api/v0/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(request.getEmail()))
        .andExpect(jsonPath("$.token").value("mock-jwt-token"))
        .andExpect(jsonPath("$.message").value("Registration successful"))
        .andExpect(jsonPath("$.expiresIn").value(86400));   

    }

    @Test
    void loginUser_Success() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("SecurePass1!");

        AuthResponse response = AuthResponse.builder()
                .email(request.getEmail())
                .token("mock-jwt-token")
                .message("Login successful")
                .expiresIn(86400L)
                .build();

        Mockito.when(authService.login(Mockito.any(LoginRequest.class))).thenReturn(response);
        mockMvc.perform(post("/api/v0/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(request.getEmail()))
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.expiresIn").value(86400));
    }

}