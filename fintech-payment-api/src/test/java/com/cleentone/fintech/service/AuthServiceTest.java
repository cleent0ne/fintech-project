package com.cleentone.fintech.service;

import com.cleentone.fintech.config.JwtUtil;
import com.cleentone.fintech.dto.*;
import com.cleentone.fintech.exception.*;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.services.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        // Inject jwtExpirationMs since it's now required by AuthResponse
        setField(authService, "jwtExpirationMs", 86400000L);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

@Test
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("Password1!");
        request.setFullName("Test User");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashedPassword");
        when(jwtUtil.generateToken(request.getEmail())).thenReturn("jwtToken");

        AuthResponse response = authService.register(request);
        assertNotNull(response);
        assertEquals("jwtToken", response.getToken());
        assertEquals("test@example.com", response.getEmail());

        verify(userRepository, times(1)).save(any(User.class));

    }

    @Test
    void register_emailAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));

    }

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");  
        request.setPassword("Password1!");

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash("hashedPassword");

         when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
         when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
         when(jwtUtil.generateToken(anyString())).thenReturn("mock_token");

    AuthResponse response = authService.login(request);
    assertNotNull(response);
    assertEquals("mock_token", response.getToken());
    }

    @Test
    void login_accountNotFound() {
        LoginRequest request = new LoginRequest();
        request.setEmail("nonexistent@example.com");

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

    }

    @Test
    void login_invalidPassword() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongpassword");

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash("correctHash");

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    }