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

/**
 * This service handles all things related to authentication and user onboarding.
 * It takes care of registering new users, setting up their initial wallets,
 * and managing logins.
 */
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

    /**
     * Handles new user registration.
     * We first check if the email is already taken, then create the user and
     * set up their default wallets (KES and USD).
     */
    public AuthResponse register(RegisterRequest request) {
        // First things first, we can't have two users with the same email.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already Exists");
        }

        // Let's create the new user record. We make sure to normalize the email
        // and securely hash the password before saving.
        User user = new User();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        userRepository.save(user);

        // Every new user needs some wallets to start with.
        createDefaultWallets(user);

        // Now we generate a JWT so the user can start using the API immediately.
        String token = jwtUtil.generateToken(user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .message("Registration successful")
                .expiresIn(jwtExpirationMs / 1000) // The client expects seconds, but we store milliseconds.
                .build();
    }

    /**
     * Authenticates a user and returns a fresh JWT.
     */
    public AuthResponse login(LoginRequest request) {
        // We look up the user by email. If they don't exist, we throw a generic 
        // error to avoid giving away which emails are registered.
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        // Check if the provided password matches the hashed one in our database.
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
    
    /**
     * Set up the initial wallets for a new user.
     * Currently, we give everyone a KES and a USD wallet.
     */
    private void createDefaultWallets(User user) {
        for (Currency currency : DEFAULT_CURRENCIES) {
    
            // Just a safety check to make sure we're not trying to create unsupported wallets.
            if (!SUPPORTED.contains(currency)) {
                throw new IllegalStateException("Currency not supported: " + currency);
            }
    
            Wallet wallet = new Wallet(user, currency);
            walletRepository.save(wallet);
    
            log.info("Wallet created: user={}, currency={}", user.getEmail(), currency);
        }
    }
}