package com.cleentone.fintech.services;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * In-memory store of invalidated JWT IDs (jti).
 *
 * On logout, the token's jti is added here with its real expiry time.
 * The JwtAuthFilter checks this store before accepting any token.
 * A scheduled task removes expired entries every 15 minutes to prevent
 * unbounded memory growth.
 *
 * NOTE: This implementation is single-node only. For multi-instance deployments,
 * replace with a Redis-backed store (e.g., Spring Data Redis + RedisTemplate).
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    // jti → absolute expiry instant
    private final ConcurrentHashMap<String, Instant> blacklist = new ConcurrentHashMap<>();

    /**
     * Blacklist a token by its jti until the token's natural expiry time.
     *
     * @param jti    the JWT ID claim
     * @param expiry the token's expiration date (from JWT claims)
     */
    public void blacklist(String jti, Date expiry) {
        blacklist.put(jti, expiry.toInstant());
        log.debug("Token blacklisted: jti={}", jti);
    }

    /**
     * Returns true if the given jti has been blacklisted.
     */
    public boolean isBlacklisted(String jti) {
        return blacklist.containsKey(jti);
    }

    /**
     * Removes entries whose token has already expired — they can no longer
     * be used anyway, so keeping them wastes memory.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void purgeExpired() {
        Instant now = Instant.now();
        int before = blacklist.size();
        blacklist.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        int removed = before - blacklist.size();
        if (removed > 0) {
            log.debug("Token blacklist purge: removed {} expired entries, {} remaining", removed, blacklist.size());
        }
    }
}
