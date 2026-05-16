package com.cleentone.fintech.integration;

import com.cleentone.fintech.dto.DepositRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Wallet Concurrency & Deadlock Integration Tests")
public class WalletConcurrencyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private com.cleentone.fintech.repository.TransactionRepository transactionRepository;
    @Autowired private WalletService walletService;
    @Autowired private PasswordEncoder passwordEncoder;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        userA = createAndRegisterUser("usera@test.com", "User A");
        userB = createAndRegisterUser("userb@test.com", "User B");

        tokenA = loginAndGetToken("usera@test.com");
        tokenB = loginAndGetToken("userb@test.com");
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
    @DisplayName("should handle 10 concurrent transfers from a limited balance wallet correctly")
    void concurrentTransfers_raceCondition_preventsOverspending() throws Exception {
        // Arrange: User A has 100 KES. 10 threads try to send 20 KES each.
        // Only 5 should succeed.
        BigDecimal initialBalance = new BigDecimal("100.00");
        deposit(userA, Currency.KES, initialBalance);

        int threadCount = 10;
        BigDecimal transferAmount = new BigDecimal("20.00");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String requestId = "req-" + i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    TransferRequest req = new TransferRequest();
                    req.setReceiverEmail("userb@test.com");
                    req.setCurrency(Currency.KES);
                    req.setAmount(transferAmount);
                    req.setRequestId(requestId);

                    MvcResult result = mockMvc.perform(post("/wallet/transfer")
                                    .header("Authorization", "Bearer " + tokenA)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    String body = result.getResponse().getContentAsString();
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else {
                        System.out.println("Transfer failed: Status " + status + ", Body: " + body);
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Act
        startLatch.countDown(); // Release all threads
        endLatch.await(); // Wait for all to finish

        // Assert
        Wallet walletA = walletRepository.findByUserAndCurrency(userA, Currency.KES).orElseThrow();
        Wallet walletB = walletRepository.findByUserAndCurrency(userB, Currency.KES).orElseThrow();

        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failureCount.get()).isEqualTo(5);
        assertThat(walletA.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(walletB.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("should handle bidirectional concurrent transfers without deadlock")
    void concurrentTransfers_bidirectional_preventsDeadlock() throws Exception {
        // Arrange: User A and User B both have 100 KES.
        // 10 threads: 5 do A -> B, 5 do B -> A.
        deposit(userA, Currency.KES, new BigDecimal("100.00"));
        deposit(userB, Currency.KES, new BigDecimal("100.00"));

        int threadCount = 10;
        BigDecimal amount = new BigDecimal("10.00");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            boolean aToB = (i % 2 == 0);
            String token = aToB ? tokenA : tokenB;
            String receiver = aToB ? "userb@test.com" : "usera@test.com";
            final String requestId = "bidir-" + i;

            executorService.submit(() -> {
                try {
                    startLatch.await();
                    TransferRequest req = new TransferRequest();
                    req.setReceiverEmail(receiver);
                    req.setCurrency(Currency.KES);
                    req.setAmount(amount);
                    req.setRequestId(requestId);

                    mockMvc.perform(post("/wallet/transfer")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andExpect(status().isOk());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Act
        startLatch.countDown();
        endLatch.await();

        // Assert: Both should still have 100.00 (50 out, 50 in)
        Wallet walletA = walletRepository.findByUserAndCurrency(userA, Currency.KES).orElseThrow();
        Wallet walletB = walletRepository.findByUserAndCurrency(userB, Currency.KES).orElseThrow();

        assertThat(walletA.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(walletB.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    private void deposit(User user, Currency currency, BigDecimal amount) {
        DepositRequest req = new DepositRequest();
        req.setCurrency(currency);
        req.setAmount(amount);
        walletService.deposit(user, req);
    }
}
