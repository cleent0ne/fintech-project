package com.cleentone.fintech.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cleentone.fintech.config.JwtUtil;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    private final String secret = Base64.getEncoder().encodeToString("CSrYLJouBPSkpRtuQ9npnkOa52xsOp26QSrVgBg9XaM=".getBytes());
    private final long expiration = 10000; // 10 seconds for testing

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();

        // inject values manually using reflection
        setField(jwtUtil, "jwtSecret", secret);
        setField(jwtUtil, "jwtExpirationMs", expiration);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    @Test
void shouldGenerateToken() {
    String token = jwtUtil.generateToken("test@mail.com");

    assertNotNull(token);
    assertFalse(token.isEmpty());
}

@Test
void shouldExtractEmailFromToken() {
    String email = "test@mail.com";

    String token = jwtUtil.generateToken(email);
    String extracted = jwtUtil.extractEmail(token);

    assertEquals(email, extracted);
}

@Test
void shouldValidateCorrectToken() {
    String token = jwtUtil.generateToken("test@mail.com");

    boolean isValid = jwtUtil.validateToken(token);

    assertTrue(isValid);
}

@Test
void shouldRejectInvalidToken() {
    String fakeToken = "this.is.not.valid.token";

    boolean isValid = jwtUtil.validateToken(fakeToken);

    assertFalse(isValid);
}


@Test
void shouldRejectTamperedToken() {
    String token = jwtUtil.generateToken("test@mail.com");

    // corrupt token
    String tampered = token + "abc";

    boolean isValid = jwtUtil.validateToken(tampered);

    assertFalse(isValid);
}

@Test
void shouldRejectExpiredToken() throws InterruptedException {
    // 100ms expiration for this test
    setField(jwtUtil, "jwtExpirationMs", 100L);
    String token = jwtUtil.generateToken("test@mail.com");

    Thread.sleep(150);

    assertFalse(jwtUtil.validateToken(token));
}

@Test
void shouldContainExpectedClaims() {
    String email = "test@mail.com";
    String token = jwtUtil.generateToken(email);

    assertEquals(email, jwtUtil.extractEmail(token));
    assertNotNull(jwtUtil.extractJti(token));
    assertNotNull(jwtUtil.extractExpiration(token));
}

}