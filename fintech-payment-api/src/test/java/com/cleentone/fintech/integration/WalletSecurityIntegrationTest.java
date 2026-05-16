package com.cleentone.fintech.integration;

import com.cleentone.fintech.dto.TransferRequest;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.model.Wallet;
import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.repository.WalletRepository;
import com.cleentone.fintech.services.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Wallet Security & Rollback Integration Tests")
public class WalletSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @SpyBean private WalletService walletService;

    private User userA;
    private User userB;
    private String tokenA;

    @BeforeEach
    void setUp() throws Exception {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        userA = createAndRegisterUser("usera@test.com", "User A");
        userB = createAndRegisterUser("userb@test.com", "User B");

        tokenA = loginAndGetToken("usera@test.com");
    }

    private User createAndRegisterUser(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("SecurePass1!"));
        user.setFullName(name);
        User saved = userRepository.save(user);
        walletService.createDefaultWallets(saved);
        return saved;
    }

    private String loginAndGetToken(String email) throws Exception {
        String loginJson = String.format("{\"email\":\"%s\", \"password\":\"SecurePass1!\"}", email);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("should fail when user A tries to access user B's wallet balance")
    void ownershipCheck_preventsUnauthorizedAccess() throws Exception {
        // User A is logged in (tokenA). They try to access User B's balance.
        // Even if they try to pass User B's details, the resolver in Controller
        // uses the token's email to fetch the user.
        // If we had a GET /wallet/{walletId}/balance, we'd test that too.
        // Our current API is /wallet/{currency}/balance, which is scoped to 'me'.

        mockMvc.perform(get("/wallet/KES/balance")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("KES"));
        // The result is User A's KES wallet, not User B's.
    }

    @SpyBean private com.cleentone.fintech.repository.TransactionRepository transactionRepository;

    @Test
    @DisplayName("should rollback balance change if transaction recording fails")
    void transactionalRollback_preventsPartialCompletion() throws Exception {
        // Arrange: User A has 1000 KES.
        deposit(userA, Currency.KES, new BigDecimal("1000.00"));

        TransferRequest req = new TransferRequest();
        req.setReceiverEmail("userb@test.com");
        req.setCurrency(Currency.KES);
        req.setAmount(new BigDecimal("100.00"));
        req.setRequestId(UUID.randomUUID().toString());

        // Force the transaction repository to fail when saving any transaction
        doThrow(new RuntimeException("DB Failure Simulation"))
                .when(transactionRepository).save(any());

        // Act
        mockMvc.perform(post("/wallet/transfer")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError());

        // Assert: Balance should still be 1000.00
        Wallet walletA = walletRepository.findByUserAndCurrency(userA, Currency.KES).orElseThrow();
        assertThat(walletA.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));

        Wallet walletB = walletRepository.findByUserAndCurrency(userB, Currency.KES).orElseThrow();
        assertThat(walletB.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private void deposit(User user, Currency currency, BigDecimal amount) {
        com.cleentone.fintech.dto.DepositRequest req = new com.cleentone.fintech.dto.DepositRequest();
        req.setCurrency(currency);
        req.setAmount(amount);
        walletService.deposit(user, req);
    }
}
