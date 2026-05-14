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

@Component
@RequiredArgsConstructor
public class WalletRateLimitFilter extends OncePerRequestFilter {


    // Key = "userId:endpoint" — each user has separate buckets per endpoint type
    // e.g. "550e8400-...:transfer" or "550e8400-...:deposit"
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
                                    throws ServletException, IOException {

        // Skip rate limiting in tests to avoid flakiness
        if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getRequestURI();
        String method = req.getMethod();

        // Only apply to wallet write operations — skip GET requests handled separately
        if (!path.startsWith("/wallet")) {
            chain.doFilter(req, res);
            return;
        }

        // Get authenticated user ID from SecurityContext
        // If not authenticated, JwtAuthFilter already handled 401 — won't reach here
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(req, res);
            return;
        }

        String userId = auth.getName(); // the email from the JWT subject claim
        String bucketKey = userId + ":" + resolveBucketType(path, method);
        Bandwidth limit = resolveLimit(path, method);

        if (limit == null) { // no limit defined for this path — pass through
            chain.doFilter(req, res);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(bucketKey,
                k -> Bucket.builder().addLimit(limit).build());

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

    private String resolveBucketType(String path, String method) {
        if ("POST".equals(method) && path.contains("transfer")) return "transfer";
        if ("POST".equals(method) && path.contains("deposit"))  return "deposit";
        if ("GET".equals(method)  && path.contains("transactions")) return "history";
        return "read"; // default for all other GET requests
    }

    private Bandwidth resolveLimit(String path, String method) {
        return switch (resolveBucketType(path, method)) {
            case "transfer"    -> Bandwidth.classic(5,  Refill.intervally(5,  Duration.ofMinutes(1)));
            case "deposit"     -> Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
            case "history"     -> Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1)));
            case "read"        -> Bandwidth.classic(30, Refill.intervally(30, Duration.ofMinutes(1)));
            default           -> null; // no limit
        };
    }
}
