package com.cleentone.fintech.config;

import com.cleentone.fintech.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * This filter protects our authentication endpoints from brute-force attacks.
 * It tracks how many requests are coming from each IP address and blocks them 
 * if they start hitting the API too fast.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    // We store the "buckets" for each IP in a cache that expires after 10 minutes of inactivity.
    private final Cache<String, Bucket> loginBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    private final Cache<String, Bucket> registerBuckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // We can skip rate limiting if it's disabled or if we're running tests.
        if (!rateLimitEnabled || java.util.Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // This particular filter only cares about login and registration attempts.
        if (!path.startsWith("/auth/login") && !path.startsWith("/auth/register")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        Bucket bucket = path.contains("login")
                ? loginBuckets.get(clientIp, k -> createLoginBucket())
                : registerBuckets.get(clientIp, k -> createRegisterBucket());

        // Try to "consume" a token from the bucket. If it's empty, the user is going too fast.
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            // Too many requests! Send back a 429 status.
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    objectMapper.writeValueAsString(
                            new ErrorResponse("RATE_LIMIT_EXCEEDED",
                                    "Too many requests. Please wait before trying again.")));
        }
    }

    /**
     * Creates a bucket that allows 10 login attempts per minute.
     */
    private Bucket createLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * Registration is more expensive, so we're stricter here: only 5 attempts per minute.
     */
    private Bucket createRegisterBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * Resolves the real IP address of the client.
     * We're careful here to only trust the X-Forwarded-For header if it comes 
     * from a trusted local proxy, otherwise anyone could spoof their IP.
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // The first IP in the list is the original client.
                return forwarded.split(",")[0].trim();
            }
        }

        return remoteAddr;
    }

    /**
     * Checks if the IP address is within a private or loopback range.
     */
    private boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        return ip.equals("127.0.0.1")
                || ip.equals("::1")
                || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || (ip.startsWith("172.") && isIn172PrivateRange(ip));
    }

    private boolean isIn172PrivateRange(String ip) {
        // Standard check for the 172.16.0.0 – 172.31.255.255 range.
        try {
            String[] parts = ip.split("\\.");
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (Exception e) {
            return false;
        }
    }
}