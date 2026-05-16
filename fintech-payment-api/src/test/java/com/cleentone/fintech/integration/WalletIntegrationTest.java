package com.cleentone.fintech.integration;

import com.cleentone.fintech.dto.DepositRequest;
import com.cleentone.fintech.dto.LoginRequest;
import com.cleentone.fintech.dto.RegisterRequest;
import com.cleentone.fintech.dto.TransferRequest;
import com.cleentone.fintech.model.enums.*;
import com.cleentone.fintech.repository.TransactionRepository;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests — loads the complete Spring context with H2 database.
 * Tests the entire stack: HTTP → Controller → Service → Repository → DB.
 *
 * These are slower than unit tests but catch bugs that mocks can't:
 * - @Transactional rollback behaviour
 * - JPA cascade operations
 * - Real constraint violations
 * - The actual request/response cycle
 *
 * @Transactional on each test rolls back all DB changes after the test.
 * This keeps tests isolated — no leftover data affects the next test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Wallet Integration Tests")
class WalletIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired TransactionRepository transactionRepository;

    // ── helpers ───────────────────────────────────────────────────────────

    private String registerAndGetToken(String email, String fullName) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("SecurePass1!");
        req.setFullName(fullName);

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private void deposit(String token, String currency, String amount) throws Exception {
        DepositRequest req = new DepositRequest();
        req.setCurrency(Currency.valueOf(currency));
        req.setAmount(new BigDecimal(amount));

        mockMvc.perform(post("/wallet/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REGISTRATION → WALLET CREATION
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("registering a user automatically creates KES and USD wallets")
    void register_createsDefaultWallets() throws Exception {
        registerAndGetToken("newuser@test.com", "New User");

        var user = userRepository.findByEmail("newuser@test.com").orElseThrow();
        var wallets = walletRepository.findByUser(user);

        assertThat(wallets).hasSize(2);
        assertThat(wallets).extracting(w -> w.getCurrency().name())
                .containsExactlyInAnyOrder("KES", "USD");
        assertThat(wallets).allMatch(w -> w.getBalance().compareTo(BigDecimal.ZERO) == 0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DEPOSIT
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deposit increases wallet balance and creates CREDIT transaction")
    void deposit_updatesBalanceAndCreatesTransaction() throws Exception {
        String token = registerAndGetToken("depositor@test.com", "Depositor");

        DepositRequest req = new DepositRequest();
        req.setCurrency(Currency.KES);
        req.setAmount(new BigDecimal("5000.00"));

        mockMvc.perform(post("/wallet/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5000.00))
                .andExpect(jsonPath("$.currency").value("KES"));

        // Verify DB state directly
        var user = userRepository.findByEmail("depositor@test.com").orElseThrow();
        var wallet = walletRepository.findByUserAndCurrency(user, Currency.KES).orElseThrow();

        assertThat(wallet.getBalance()).isEqualByComparingTo("5000.00");

        var txPage = transactionRepository.findByWallet(wallet,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(txPage.getTotalElements()).isEqualTo(1);
        assertThat(txPage.getContent().get(0).getType())
                .isEqualTo(TransactionType.CREDIT);
        assertThat(txPage.getContent().get(0).getBalanceAfter())
                .isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("deposit without token returns 401")
    void deposit_noToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("currency", "KES", "amount", 100));

        mockMvc.perform(post("/wallet/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TRANSFER
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("transfer moves money between users and creates two linked transactions")
    void transfer_movesMoneyAndCreatesLinkedTransactions() throws Exception {
        String senderToken   = registerAndGetToken("sender@test.com", "Sender");
        String receiverToken = registerAndGetToken("receiver@test.com", "Receiver");

        // Sender deposits KES 3000
        deposit(senderToken, "KES", "3000.00");

        // Transfer KES 1000 to receiver
        TransferRequest req = new TransferRequest();
        req.setReceiverEmail("receiver@test.com");
        req.setCurrency(Currency.KES);
        req.setAmount(new BigDecimal("1000.00"));
        req.setRequestId(java.util.UUID.randomUUID().toString());
        req.setDescription("Integration test transfer");

        mockMvc.perform(post("/wallet/transfer")
                        .header("Authorization", "Bearer " + senderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender_new_balance").value(2000.00))
                .andExpect(jsonPath("$.receiver_email").value("receiver@test.com"))
                .andExpect(jsonPath("$.transaction_reference").isNotEmpty());

        // Verify sender balance
        var sender = userRepository.findByEmail("sender@test.com").orElseThrow();
        var senderWallet = walletRepository.findByUserAndCurrency(sender, Currency.KES).orElseThrow();
        assertThat(senderWallet.getBalance()).isEqualByComparingTo("2000.00");

        // Verify receiver balance
        var receiver = userRepository.findByEmail("receiver@test.com").orElseThrow();
        var receiverWallet = walletRepository.findByUserAndCurrency(receiver, Currency.KES).orElseThrow();
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo("1000.00");

        // Verify two linked transaction records
        var senderTxPage = transactionRepository.findByWallet(senderWallet,
                org.springframework.data.domain.PageRequest.of(0, 10));
        var receiverTxPage = transactionRepository.findByWallet(receiverWallet,
                org.springframework.data.domain.PageRequest.of(0, 10));

        // Sender has DEBIT, receiver has CREDIT
        assertThat(senderTxPage.getContent()).anyMatch(
                t -> t.getType() == TransactionType.DEBIT);
        assertThat(receiverTxPage.getContent()).anyMatch(
                t -> t.getType() == TransactionType.CREDIT);

        // Transactions are linked
        var debit  = senderTxPage.getContent().stream()
                .filter(t -> t.getType() == TransactionType.DEBIT)
                .findFirst().orElseThrow();
        var credit = receiverTxPage.getContent().get(0);

        assertThat(debit.getRelatedTransaction()).isNotNull();
        assertThat(debit.getRelatedTransaction().getId()).isEqualTo(credit.getId());
    }

    @Test
    @DisplayName("transfer fails with 422 when sender has insufficient funds")
    void transfer_insufficientFunds_returns422_noBalanceChange() throws Exception {
        String senderToken   = registerAndGetToken("broker@test.com", "Broke Sender");
        String receiverToken = registerAndGetToken("rich@test.com", "Rich Receiver");

        // Sender has only KES 100
        deposit(senderToken, "KES", "100.00");

        TransferRequest req = new TransferRequest();
        req.setReceiverEmail("rich@test.com");
        req.setCurrency(Currency.KES);
        req.setAmount(new BigDecimal("500.00")); // more than balance
        req.setRequestId(java.util.UUID.randomUUID().toString());

        mockMvc.perform(post("/wallet/transfer")
                        .header("Authorization", "Bearer " + senderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));

        // Verify no balance change
        var sender = userRepository.findByEmail("broker@test.com").orElseThrow();
        var senderWallet = walletRepository.findByUserAndCurrency(sender, Currency.KES).orElseThrow();
        assertThat(senderWallet.getBalance()).isEqualByComparingTo("100.00");

        // Verify no transaction records created for the failed transfer
        var receiverUser = userRepository.findByEmail("rich@test.com").orElseThrow();
        var receiverWallet = walletRepository.findByUserAndCurrency(receiverUser, Currency.KES).orElseThrow();
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("transfer to self returns 422")
    void transfer_selfTransfer_returns422() throws Exception {
        String token = registerAndGetToken("self@test.com", "Self Sender");
        deposit(token, "KES", "1000.00");

        TransferRequest req = new TransferRequest();
        req.setReceiverEmail("self@test.com"); // own email
        req.setCurrency(Currency.KES);
        req.setAmount(new BigDecimal("100.00"));
        req.setRequestId(java.util.UUID.randomUUID().toString());

        mockMvc.perform(post("/wallet/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_TRANSFER"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("yourself")));
    }

    @Test
    @DisplayName("transfer to unknown receiver returns 404")
    void transfer_unknownReceiver_returns404() throws Exception {
        String token = registerAndGetToken("known@test.com", "Known Sender");
        deposit(token, "KES", "1000.00");

        TransferRequest req = new TransferRequest();
        req.setReceiverEmail("nobody@test.com"); // doesn't exist
        req.setCurrency(Currency.KES);
        req.setAmount(new BigDecimal("100.00"));
        req.setRequestId(java.util.UUID.randomUUID().toString());

        mockMvc.perform(post("/wallet/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TRANSACTION HISTORY
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("transaction history returns paginated results sorted newest first")
    void getTransactions_returnsPaginatedHistory() throws Exception {
        String token = registerAndGetToken("history@test.com", "History User");

        // Make 3 deposits
        deposit(token, "KES", "100.00");
        deposit(token, "KES", "200.00");
        deposit(token, "KES", "300.00");

        mockMvc.perform(get("/wallet/KES/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UNKNOWN ROUTES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("unknown route returns 404 ROUTE_NOT_FOUND")
    void unknownRoute_returns404() throws Exception {
        String token = registerAndGetToken("routetest@test.com", "Route Tester");

        mockMvc.perform(get("/wallet/doesnotexist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ROUTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("wrong HTTP method returns 405 METHOD_NOT_ALLOWED")
    void wrongMethod_returns405() throws Exception {
        mockMvc.perform(get("/auth/login")) // login is POST only
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }
}