package com.cleentone.fintech.services;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);


    private final ConcurrentHashMap<String, Instant> blacklist = new ConcurrentHashMap<>();

   
    public void blacklist(String jti, Date expiry) {
        blacklist.put(jti, expiry.toInstant());
        log.debug("Token blacklisted: jti={}", jti);
    }


    public boolean isBlacklisted(String jti) {
        return blacklist.containsKey(jti);
    }

  
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
