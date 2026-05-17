package com.cleentone.fintech.config;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * A utility class for everything related to JSON Web Tokens (JWTs).
 * This class handles token generation, parsing, and validation.
 */
@Component
public class JwtUtil {

    private static final String ISSUER   = "fintech-payment-api";
    private static final String AUDIENCE = "fintech-clients";

    @Value("${spring.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Decodes our secret key and prepares it for signing tokens.
     */
    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Creates a new JWT for a specific user email.
     * We include a unique JTI (ID) so we can blacklist tokens later if needed.
     */
    public String generateToken(String email) {
        return Jwts.builder()
                .setId(UUID.randomUUID().toString()) // The JTI — essential for revoking tokens.
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Pulls the user's email (the subject) out of the token.
     */
    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Extracts the unique token ID (JTI).
     */
    public String extractJti(String token) {
        return getClaims(token).getId();
    }

    /**
     * Checks when the token is set to expire.
     */
    public Date extractExpiration(String token) {
        return getClaims(token).getExpiration();
    }

    /**
     * Validates that the token hasn't been tampered with and hasn't expired.
     * We also check that the issuer and audience match what we expect.
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            
            if (!ISSUER.equals(claims.getIssuer()))     return false;
            if (!AUDIENCE.equals(claims.getAudience())) return false;
            return true;
        } catch (ExpiredJwtException e) {
            // Token is old.
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            // Something else is wrong with the token (e.g., bad signature).
            return false;
        }
    }

    /**
     * A helper to parse the JWT and get all the claims out of it.
     */
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}