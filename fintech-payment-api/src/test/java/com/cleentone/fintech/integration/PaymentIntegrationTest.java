package com.cleentone.fintech.integration;

import com.cleentone.fintech.dto.CallbackRequest;
import com.cleentone.fintech.dto.DepositRequest;
import com.cleentone.fintech.dto.PaymentRequest;
import com.cleentone.fintech.dto.RegisterRequest;
import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.model.enums.PaymentStatus;
import com.cleentone.fintech.model.enums.TransactionType;
import com.cleentone.fintech.repository.PaymentRepository;
import com.cleentone.fintech.repository.TransactionRepository;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real integration tests for the Asynchronous Payment Layer.
 * Verifies full end-to-end user flows, authorization contexts, HMAC webhooks, 
 * wallet debits, and ledger logs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Payment Integration Tests")
class PaymentIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired TransactionRepository transactionRepository;

    private static final String DEFAULT_SECRET = "mpesa-simulation-webhook-default-secret-key-2026";

    // ── helpers ───────────────────────────────────────────────────────────

    private String registerAndGetToken(String email, String fullName) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("SecurePass1!");
        req.setFullName(fullName);

        MvcResult result = mockMvc.perform(post("/api/v0/auth/register")
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

        mockMvc.perform(post("/api/v0/wallet/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private String computeHmacSignature(UUID paymentId, String status, String failureReason) throws Exception {
        String dataToSign = paymentId.toString() + ":" + status;
        if (failureReason != null) {
            dataToSign += ":" + failureReason;
        }

        Mac sha256HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256HMAC.init(secretKey);

        byte[] hashBytes = sha256HMAC.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // END-TO-END PAYMENT FLOW
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("full payment initiation, status poll, and success webhook callback flow")
    void e2ePaymentFlow_success() throws Exception {
        String token = registerAndGetToken("payer@test.com", "Payer User");

        // 1. Payer deposits KES 1000
        deposit(token, "KES", "1000.00");

        // 2. Initiate Payment
        PaymentRequest req = new PaymentRequest();
        req.setAmount(new BigDecimal("300.00"));
        req.setCurrency(Currency.KES);
        req.setIdempotencyKey("payment-idemp-1");

        MvcResult initResult = mockMvc.perform(post("/api/v0/payments/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(300.00))
                .andExpect(jsonPath("$.currency").value("KES"))
                .andExpect(jsonPath("$.payment_id").isNotEmpty())
                .andReturn();

        String initBody = initResult.getResponse().getContentAsString();
        String paymentIdStr = objectMapper.readTree(initBody).get("payment_id").asText();
        UUID paymentId = UUID.fromString(paymentIdStr);

        // Verify direct state in database
        var paymentOpt = paymentRepository.findById(paymentId);
        assertThat(paymentOpt).isPresent();
        assertThat(paymentOpt.get().getStatus()).isEqualTo(PaymentStatus.PENDING);

        // 3. Query Payment Status
        mockMvc.perform(get("/api/v0/payments/" + paymentIdStr + "/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payment_id").value(paymentIdStr));

        // 4. Simulate external webhook callback SUCCESS
        CallbackRequest callback = new CallbackRequest();
        callback.setPaymentId(paymentId);
        callback.setStatus("SUCCESS");
        callback.setSignature(computeHmacSignature(paymentId, "SUCCESS", null));

        mockMvc.perform(post("/api/v0/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk());

        // 5. Query status again to verify SUCCESS and balance debit
        mockMvc.perform(get("/api/v0/payments/" + paymentIdStr + "/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // Verify balance updated to KES 700.00
        var user = userRepository.findByEmail("payer@test.com").orElseThrow();
        var wallet = walletRepository.findByUserAndCurrency(user, Currency.KES).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo("700.00");

        // Verify a ledger DEBIT transaction was written
        var txPage = transactionRepository.findByWallet(wallet,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(txPage.getTotalElements()).isEqualTo(2); // 1 deposit CREDIT, 1 payment DEBIT
        var debitTx = txPage.getContent().stream()
                .filter(t -> t.getType() == TransactionType.DEBIT)
                .findFirst();
        assertThat(debitTx).isPresent();
        assertThat(debitTx.get().getAmount()).isEqualByComparingTo("300.00");
        assertThat(debitTx.get().getBalanceAfter()).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("payment initiation fails due to insufficient funds")
    void initiatePayment_insufficientFunds_fails() throws Exception {
        String token = registerAndGetToken("broke-payer@test.com", "Broke Payer");

        // Broke payer has KES 0 balance

        PaymentRequest req = new PaymentRequest();
        req.setAmount(new BigDecimal("50.00")); // more than 0
        req.setCurrency(Currency.KES);
        req.setIdempotencyKey("payment-broke-1");

        mockMvc.perform(post("/api/v0/payments/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("webhook callback with invalid signature returns 500 status due to SecurityException")
    void callback_invalidSignature_returns500() throws Exception {
        String token = registerAndGetToken("hacker@test.com", "Hacker Payer");
        deposit(token, "KES", "500.00");

        PaymentRequest req = new PaymentRequest();
        req.setAmount(new BigDecimal("100.00"));
        req.setCurrency(Currency.KES);
        req.setIdempotencyKey("hacker-key-1");

        MvcResult initResult = mockMvc.perform(post("/api/v0/payments/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andReturn();

        String initBody = initResult.getResponse().getContentAsString();
        String paymentIdStr = objectMapper.readTree(initBody).get("payment_id").asText();
        UUID paymentId = UUID.fromString(paymentIdStr);

        // Issue callback with fake signature
        CallbackRequest callback = new CallbackRequest();
        callback.setPaymentId(paymentId);
        callback.setStatus("SUCCESS");
        callback.setSignature("completely-invalid-signature-hash");

        mockMvc.perform(post("/api/v0/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().is5xxServerError()); 
    }
}
