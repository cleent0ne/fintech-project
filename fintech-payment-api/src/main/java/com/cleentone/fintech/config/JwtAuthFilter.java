package com.cleentone.fintech.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cleentone.fintech.services.CustomUserDetailsService;
import com.cleentone.fintech.services.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * This filter sits at the front door of every request. It looks for a JWT in the 
 * Authorization header and, if it finds a valid one, it sets up the security 
 * context so the rest of the app knows who the user is.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // We check if there's an Authorization header and if it looks like a Bearer token.
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No token? No problem, just move on to the next filter. 
            // Secure endpoints will eventually block the request if authentication is missing.
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // First, check if the token's signature and expiration are valid.
        if (!jwtUtil.validateToken(token)) {
            sendUnauthorized(response, "Token is invalid or expired");
            return;
        }

        // Second, check if this token has been blacklisted (e.g., if the user logged out).
        String jti = jwtUtil.extractJti(token);
        if (tokenBlacklistService.isBlacklisted(jti)) {
            sendUnauthorized(response, "Token has been revoked");
            return;
        }

        String email = jwtUtil.extractEmail(token);

        // If we have an email and the user isn't already authenticated for this request...
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // We load the user details from our database.
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

                // Then we create an authentication token and put it in Spring Security's context.
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authToken);

            } catch (Exception e) {
                // If anything goes wrong during authentication, we clear the context just to be safe.
                SecurityContextHolder.clearContext();
            }
        }

        // Finally, we let the request continue its journey.
        filterChain.doFilter(request, response);
    }

    /**
     * A helper to send a clean JSON error response if authentication fails.
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        java.util.Map.of("error", "UNAUTHORIZED", "message", message)
                )
        );
    }
}