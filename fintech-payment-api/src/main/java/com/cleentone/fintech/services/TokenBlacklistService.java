package com.cleentone.fintech.services;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * This service keeps track of JWTs that have been logged out or invalidated 
 * before they actually expire. It's an in-memory store, so it's very fast, 
 * but it will clear out if the server restarts.
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    // We use a ConcurrentHashMap to safely handle requests from multiple threads.
    private final ConcurrentHashMap<String, Instant> blacklist = new ConcurrentHashMap<>();

    /**
     * Adds a token's unique ID (JTI) to the blacklist.
     * Once it's here, the token is no longer considered valid.
     */
    public void blacklist(String jti, Date expiry) {
        blacklist.put(jti, expiry.toInstant());
        log.debug("Token blacklisted: jti={}", jti);
    }

    /**
     * Checks if a specific token has been blacklisted.
     */
    public boolean isBlacklisted(String jti) {
        return blacklist.containsKey(jti);
    }

    /**
     * Every 15 minutes, we clean up the blacklist to remove tokens that have
     * naturally expired. This keeps the memory usage from growing indefinitely.
     */
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void purgeExpired() {
        Instant now = Instant.now();
        int before = blacklist.size();
        
        // Remove entries where the expiry time is in the past.
        blacklist.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        
        int removed = before - blacklist.size();
        if (removed > 0) {
            log.debug("Token blacklist purge: removed {} expired entries, {} remaining", removed, blacklist.size());
        }
    }
}
