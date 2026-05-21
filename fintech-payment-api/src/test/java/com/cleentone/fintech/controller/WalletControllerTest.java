package com.cleentone.fintech.controller;

import com.cleentone.fintech.controllers.WalletController;
import com.cleentone.fintech.dto.*;
import com.cleentone.fintech.exception.InsufficientFundsException;
import com.cleentone.fintech.exception.InvalidTransferException;
import com.cleentone.fintech.exception.ResourceNotFoundException;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.model.enums.*;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.services.WalletService;
import com.cleentone.fintech.services.CustomUserDetailsService;
import com.cleentone.fintech.services.TokenBlacklistService;
import com.cleentone.fintech.config.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(WalletController.class)
@DisplayName("WalletController")
class WalletControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean WalletService walletService;
    @MockBean UserRepository userRepository;
    @MockBean JwtUtil jwtUtil;
    @MockBean CustomUserDetailsService customUserDetailsService;
    @MockBean TokenBlacklistService tokenBlacklistService;

    // ── Shared fixtures ──────────────────────────────────────────────────────
    
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("test@test.com");

        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
    }

    private WalletResponse kesWalletResponse() {
        // Build a mock WalletResponse for KES
        WalletResponse r = new WalletResponse();
        // Using reflection-style: set via from() — in tests use a helper
        return WalletResponse.from(mockKesWallet());
    }

    private com.cleentone.fintech.model.Wallet mockKesWallet() {
        com.cleentone.fintech.model.User user = new com.cleentone.fintech.model.User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");

        com.cleentone.fintech.model.Wallet wallet =
                new com.cleentone.fintech.model.Wallet(user, Currency.KES);
        wallet.setId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("1000.00"));
        return wallet;
    }

    // ════════════════════════════════════════════════════════════════════════
    // GET /wallet/balances
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET api/v0/wallet/balances")
    class GetBalances {

        @Test
        @WithMockUser(username = "test@test.com")
        @DisplayName("should return 200 with list of wallets for authenticated user")
        void getBalances_authenticated_returns200() throws Exception {
            when(walletService.getAllWallets(any())).thenReturn(List.of(kesWalletResponse()));

            mockMvc.perform(get("/api/v0/wallet/balances"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].currency").value("KES"))
                    .andExpect(jsonPath("$[0].balance").exists())
                    .andExpect(jsonPath("$[0].formatted_balance").exists());
        }

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void getBalances_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v0/wallet/balances"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // POST /wallet/deposit
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST api/v0/wallet/deposit")
    class Deposit {

        @Test
        @WithMockUser(username = "test@test.com")
        @DisplayName("should return 200 with updated wallet after valid deposit")
        void deposit_valid_returns200() throws Exception {
            when(walletService.deposit(any(), any())).thenReturn(kesWalletResponse());

            String body = """
                {"currency":"KES","amount":500.00}
                """;

            mockMvc.perform(post("/api/v0/wallet/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").exists())
                    .andExpect(jsonPath("$.currency").value("KES"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 when amount is negative")
        void deposit_negativeAmount_returns400() throws Exception {
            String body = """
                {"currency":"KES","amount":-100.00}
                """;

            mockMvc.perform(post("/api/v0/wallet/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 when amount is zero")
        void deposit_zeroAmount_returns400() throws Exception {
            String body = """
                {"currency":"KES","amount":0}
                """;

            mockMvc.perform(post("/api/v0/wallet/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 when currency is missing")
        void deposit_missingCurrency_returns400() throws Exception {
            String body = """
                {"amount":500.00}
                """;

            mockMvc.perform(post("/api/v0/wallet/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 when amount has more than 2 decimal places")
        void deposit_tooManyDecimalPlaces_returns400() throws Exception {
            String body = """
                {"currency":"KES","amount":100.123}
                """;

            mockMvc.perform(post("/api/v0/wallet/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 when amount exceeds maximum")
        void deposit_exceedsMaxAmount_returns400() throws Exception {
            String body = """
                {"currency":"KES","amount":9999999.00}
                """;

            mockMvc.perform(post("/api/v0/wallet/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void deposit_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v0/wallet/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currency\":\"KES\",\"amount\":100}")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // POST /wallet/transfer
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST api/v0/wallet/transfer")
    class Transfer {

        private TransferResponse mockTransferResponse() {
            return new TransferResponse(
                    "Transfer successful",
                    Currency.KES,
                    new BigDecimal("300.00"),
                    new BigDecimal("700.00"),
                    "receiver@test.com",
                    UUID.randomUUID().toString()
            );
        }

        @Test
        @WithMockUser(username = "sender@test.com")
        @DisplayName("should return 200 with transfer details on success")
        void transfer_valid_returns200() throws Exception {
            when(walletService.transfer(any(), any())).thenReturn(mockTransferResponse());

            String body = """
                {
                    "receiverEmail":"receiver@test.com",
                    "currency":"KES",
                    "amount":300.00,
                    "requestId":"req-123",
                    "description":"Test"
                }
                """;

            mockMvc.perform(post("/api/v0/wallet/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Transfer successful"))
                    .andExpect(jsonPath("$.sender_new_balance").value(700.00))
                    .andExpect(jsonPath("$.transaction_reference").exists());
        }

        @Test
        @WithMockUser
        @DisplayName("should return 422 for insufficient funds")
        void transfer_insufficientFunds_returns422() throws Exception {
            when(walletService.transfer(any(), any()))
                    .thenThrow(new InsufficientFundsException("Insufficient funds"));

            String body = """
                {"receiverEmail":"r@test.com","currency":"KES","amount":9999.00,"requestId":"req-456"}
                """;

            mockMvc.perform(post("/api/v0/wallet/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 422 for self-transfer")
        void transfer_selfTransfer_returns422() throws Exception {
            when(walletService.transfer(any(), any()))
                    .thenThrow(new InvalidTransferException("Cannot transfer to yourself"));

            String body = """
                {"receiverEmail":"me@test.com","currency":"KES","amount":100.00,"requestId":"req-789"}
                """;

            mockMvc.perform(post("/api/v0/wallet/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("INVALID_TRANSFER"))
                    .andExpect(jsonPath("$.message").value("Cannot transfer to yourself"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 404 when receiver not found")
        void transfer_receiverNotFound_returns404() throws Exception {
            when(walletService.transfer(any(), any()))
                    .thenThrow(new ResourceNotFoundException("Recipient not found"));

            String body = """
                {"receiverEmail":"ghost@test.com","currency":"KES","amount":100.00,"requestId":"req-abc"}
                """;

            mockMvc.perform(post("/api/v0/wallet/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 when receiver email is invalid format")
        void transfer_invalidReceiverEmail_returns400() throws Exception {
            String body = """
                {"receiverEmail":"not-an-email","currency":"KES","amount":100.00}
                """;

            mockMvc.perform(post("/api/v0/wallet/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 when amount is missing")
        void transfer_missingAmount_returns400() throws Exception {
            String body = """
                {"receiverEmail":"r@test.com","currency":"KES"}
                """;

            mockMvc.perform(post("/api/v0/wallet/transfer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // GET /wallet/{currency}/transactions
    // ════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET api/v0/wallet/{currency}/transactions")
    class TransactionHistory {

        @Test
        @WithMockUser
        @DisplayName("should return 200 with paginated transaction list")
        void getTransactions_returns200WithPagination() throws Exception {
            Page<TransactionResponse> emptyPage = Page.empty();
            when(walletService.getTransactionHistory(any(), any(), anyInt(), anyInt()))
                    .thenReturn(emptyPage);

            mockMvc.perform(get("/api/v0/wallet/KES/transactions")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").exists())
                    .andExpect(jsonPath("$.totalPages").exists());
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 for invalid currency in path")
        void getTransactions_invalidCurrency_returns400() throws Exception {
            mockMvc.perform(get("/api/v0/wallet/INVALID/transactions"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void getTransactions_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v0/wallet/KES/transactions"))
                    .andExpect(status().isUnauthorized());
        }
    }
}