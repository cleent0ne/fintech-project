package com.cleentone.fintech.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cleentone.fintech.config.JwtUtil;
import com.cleentone.fintech.dto.AuthResponse;
import com.cleentone.fintech.dto.LoginRequest;
import com.cleentone.fintech.dto.RegisterRequest;
import com.cleentone.fintech.exception.EmailAlreadyExistsException;
import com.cleentone.fintech.exception.InvalidCredentialsException;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.model.Wallet;
import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WalletRepository walletRepository;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    
    @Value("${spring.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already Exists");
        }

        User user = new User();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        userRepository.save(user);

        // Call this inside register() after userRepository.save(user):
createDefaultWallets(user);

        String token = jwtUtil.generateToken(user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .message("Registration successful")
                .expiresIn(jwtExpirationMs / 1000) // convert ms → seconds for the client
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .message("Login successful")
                .expiresIn(jwtExpirationMs / 1000)
                .build();
    }

    private static final List<Currency> DEFAULT_CURRENCIES = List.of(
        Currency.KES,
        Currency.USD
    );

    private static final Set<Currency> SUPPORTED = Set.of(
        Currency.KES,
        Currency.USD
    );
    
    private void createDefaultWallets(User user) {
        for (Currency currency : DEFAULT_CURRENCIES) {
    
            if (!SUPPORTED.contains(currency)) {
                throw new IllegalStateException("Currency not supported: " + currency);
            }
    
            Wallet wallet = new Wallet(user, currency);
            walletRepository.save(wallet);
    
            log.info("Wallet created: user={}, currency={}", user.getEmail(), currency);
        }
    }

  

}