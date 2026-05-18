package com.cleentone.fintech.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

import com.cleentone.fintech.config.JwtAuthFilter;
import com.cleentone.fintech.config.RateLimitFilter;
import com.cleentone.fintech.services.CustomUserDetailsService;

/**
 * This is the main security configuration for the application.
 * It defines which endpoints are public, how we handle logins, and sets up
 * our security filters like JWT authentication and rate limiting.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    /**
     * Configures the HTTP security filter chain.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // We're building a stateless API, so we can safely disable CSRF.
            .csrf(AbstractHttpConfigurer::disable)
            
            // Security header to prevent the site from being framed.
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.deny())
            )

            // Define which parts of the API are open to everyone and which need a login.
            .authorizeHttpRequests(auth -> auth
                // These endpoints (registration, login, logout, and API docs) don't need a token.
                .requestMatchers("/auth/register", "/auth/login", "/auth/logout", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/payments/callback").permitAll()
                
                // For everything else, you must have a valid JWT.
                .anyRequest().authenticated()
            )
            
            // How to handle situations where someone tries to access a protected resource without being logged in.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(unauthorizedEntryPoint())
            )
            
            // We don't want Spring creating sessions; we're using JWTs for every request.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            .authenticationProvider(authenticationProvider())
            
            // Order matters here! We check the rate limit first, then authenticate the user.
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Custom entry point to return a nice JSON error when authentication fails.
     */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("error", "UNAUTHORIZED", "message", "Full authentication is required to access this resource")
            ));
        };
    }

    /**
     * Connects our custom UserDetailsService and PasswordEncoder to Spring Security.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Standard AuthenticationManager bean.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * We use BCrypt for hashing passwords. Strength 10 is a good balance between security and speed.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}