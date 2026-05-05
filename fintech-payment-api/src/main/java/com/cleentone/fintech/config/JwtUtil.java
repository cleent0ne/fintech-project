package com.cleentone.fintech.config;

import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    @Value("${spring.jwt.secret}")
    private String jwtSecret;
    
    @Value("${spring.jwt.expiration-ms}")
    private long jwtExpirationMs;

    private Key getSigningKey(){
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);

    }

    public String generateToken(String email){
        return Jwts.builder()
        .setSubject(email)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
        .signWith(getSigningKey(), SignatureAlgorithm.HS256)
        .compact();
    }

    public String extractEmail(String token){
        return Jwts.parserBuilder()
    .setSigningKey(getSigningKey()).build()
    .parseClaimsJws(token)
    .getBody().getSubject();
    }

    public boolean validateToken (String token){
        try {
            Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token);

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

}
