package com.cleentone.fintech.config;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cleentone.fintech.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;

/**
 * While the RateLimitFilter protects our auth endpoints from external attacks,
 * this filter ensures that logged-in users don't spam our wallet endpoints.
 * It tracks limits on a per-user, per-endpoint basis.
 */
@Component
@RequiredArgsConstructor
public class WalletRateLimitFilter extends OncePerRequestFilter {

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    // We store separate "buckets" for each user and each type of action they take.
    // Example keys: "user@email.com:transfer" or "user@email.com:deposit"
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
                                    throws ServletException, IOException {

        // Skip rate limiting if it's explicitly disabled or if we're running tests.
        if (!rateLimitEnabled || java.util.Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getRequestURI();
        String method = req.getMethod();

        // This filter specifically targets wallet-related endpoints.
        if (!path.startsWith("/wallet")) {
            chain.doFilter(req, res);
            return;
        }

        // We need to know who the user is to apply per-user limits.
        // If they aren't authenticated, we just pass through; the security layer will handle it.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(req, res);
            return;
        }

        String userId = auth.getName(); // This is the user's email.
        String bucketKey = userId + ":" + resolveBucketType(path, method);
        Bandwidth limit = resolveLimit(path, method);

        // If no limit is defined for this specific path, just let them through.
        if (limit == null) {
            chain.doFilter(req, res);
            return;
        }

        // Get or create the bucket for this user/action combination.
        Bucket bucket = buckets.computeIfAbsent(bucketKey,
                k -> Bucket.builder().addLimit(limit).build());

        // Try to take a token. If it's successful, continue; otherwise, send 429.
        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write(objectMapper.writeValueAsString(
                new ErrorResponse("RATE_LIMIT_EXCEEDED",
                    "Too many requests. Please wait before retrying.")));
        }
    }

    /**
     * Categorizes the request into a "bucket type" so we can apply different limits.
     */
    private String resolveBucketType(String path, String method) {
        if ("POST".equals(method) && path.contains("transfer")) return "transfer";
        if ("POST".equals(method) && path.contains("deposit"))  return "deposit";
        if ("GET".equals(method)  && path.contains("transactions")) return "history";
        return "read"; // A general bucket for other GET requests.
    }

    /**
     * Defines the actual limits for each action type.
     */
    private Bandwidth resolveLimit(String path, String method) {
        return switch (resolveBucketType(path, method)) {
            case "transfer"    -> Bandwidth.classic(5,  Refill.intervally(5,  Duration.ofMinutes(1)));
            case "deposit"     -> Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
            case "history"     -> Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1)));
            case "read"        -> Bandwidth.classic(30, Refill.intervally(30, Duration.ofMinutes(1)));
            default           -> null;
        };
    }
}
