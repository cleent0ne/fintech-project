package com.cleentone.fintech.security;

import com.cleentone.fintech.config.JwtUtil;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    private static final String TEST_EMAIL = "test@mail.com";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setPasswordHash("hashedPassword"); // logic doesn't check password in filter
        user.setFullName("Test User");
        userRepository.save(user);
    }



    @Test
void shouldRejectRequestWithoutToken() throws Exception {
    mockMvc.perform(get("/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
}

@Test
void shouldAllowRequestWithValidToken() throws Exception {

    String token = jwtUtil.generateToken(TEST_EMAIL);

    mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
}

@Test
void shouldRejectInvalidToken() throws Exception {

    mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer invalid.token.here"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Token is invalid or expired"));
}

@Test
void shouldBlacklistTokenOnLogout() throws Exception {
    String token = jwtUtil.generateToken(TEST_EMAIL);

    // 1. Token works initially
    mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

    // 2. Logout
    mockMvc.perform(post("/auth/logout")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Logged out successfully"));

    // 3. Token is now rejected
    mockMvc.perform(get("/auth/me")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Token has been revoked"));
}



































}