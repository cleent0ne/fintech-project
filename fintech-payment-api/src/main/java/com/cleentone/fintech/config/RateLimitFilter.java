package com.cleentone.fintech.config;

import com.cleentone.fintech.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // One bucket per IP address — in production, back this with Redis
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only rate limit auth endpoints
        if (!path.startsWith("/auth/login") && !path.startsWith("/auth/register")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        Bucket bucket = path.contains("login")
                ? loginBuckets.computeIfAbsent(clientIp, k -> createLoginBucket())
                : registerBuckets.computeIfAbsent(clientIp, k -> createRegisterBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            // Rate limit exceeded — return 429
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    objectMapper.writeValueAsString(
                            new ErrorResponse("RATE_LIMIT_EXCEEDED",
                                    "Too many requests. Please wait before trying again.")));
        }
    }

    private Bucket createLoginBucket() {
        // 10 attempts per minute — generous but blocks automated attacks
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
    }

    private Bucket createRegisterBucket() {
        // 5 registrations per minute per IP — stops mass account creation
        return Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For first (set by load balancers / proxies)
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim(); // first IP in chain is the real client
        }
        System.out.println("Client IP: " + request.getRemoteAddr());
        return request.getRemoteAddr();
    }
}