package com.cleentone.fintech.services;

import com.cleentone.fintech.dto.CallbackRequest;
import com.cleentone.fintech.dto.PaymentRequest;
import com.cleentone.fintech.dto.PaymentResponse;
import com.cleentone.fintech.event.PaymentEventPublisher;
import com.cleentone.fintech.exception.DuplicatePaymentException;
import com.cleentone.fintech.exception.InsufficientFundsException;
import com.cleentone.fintech.exception.InvalidPaymentStateException;
import com.cleentone.fintech.exception.PaymentNotFoundException;
import com.cleentone.fintech.exception.ResourceNotFoundException;
import com.cleentone.fintech.model.Payment;
import com.cleentone.fintech.model.Transaction;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.model.Wallet;
import com.cleentone.fintech.model.enums.PaymentStatus;
import com.cleentone.fintech.model.enums.TransactionType;
import com.cleentone.fintech.repository.PaymentRepository;
import com.cleentone.fintech.repository.TransactionRepository;
import com.cleentone.fintech.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles transactional operations and state changes for the Asynchronous Payment Layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    @Value("${fintech.payment.webhook.secret:mpesa-simulation-webhook-default-secret-key-2026}")
    private String webhookSecret;

    /**
     * Initiates an asynchronous payment transaction.
     * Enforces idempotency verification, checks sender wallet balance, and triggers async processing.
     *
     * @param user    The user initiating the payment.
     * @param request The payment request payload.
     * @return PaymentResponse containing initial details of the pending payment.
     */
    @Transactional
    public PaymentResponse initiate(User user, PaymentRequest request) {
        log.info("Initiating payment request for user: {} (key: {})", user.getEmail(), request.getIdempotencyKey());

        // 1. Idempotency Check
        Optional<Payment> existingOpt = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            
            // Validate if the existing transaction matches the new request details
            boolean detailsMatch = existing.getUser().getId().equals(user.getId())
                && existing.getAmount().compareTo(request.getAmount()) == 0
                && existing.getCurrency() == request.getCurrency();

            if (detailsMatch) {
                log.info("Duplicate identical payment request detected for key: {}. Returning existing record.", request.getIdempotencyKey());
                return PaymentResponse.from(existing);
            } else {
                log.warn("Idempotency conflict: key {} used with different payment details.", request.getIdempotencyKey());
                throw new DuplicatePaymentException("A payment has already been initiated with this idempotency key but different request details.");
            }
        }

        // 2. Wallet Balance Check
        Wallet wallet = walletRepository.findByUserAndCurrency(user, request.getCurrency())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Wallet not found for user in currency " + request.getCurrency()
            ));

        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Insufficient funds for user {}: required {} {}, available {} {}", 
                user.getEmail(), request.getAmount(), request.getCurrency(), wallet.getBalance(), wallet.getCurrency());
            throw new InsufficientFundsException(
                "Insufficient funds. Available: " + wallet.getCurrency() + " " + wallet.getBalance()
            );
        }

        // 3. Persist Payment in PENDING state
        Payment payment = new Payment(user, request.getAmount(), request.getCurrency(), request.getIdempotencyKey());
        payment = paymentRepository.save(payment);

        log.info("Payment saved successfully in PENDING state (ID: {})", payment.getId());

        // 4. Publish Spring event for asynchronous processing
        paymentEventPublisher.publishPaymentInitiated(payment.getId());
        log.info("PaymentInitiatedEvent published via publisher for payment ID: {}", payment.getId());

        return PaymentResponse.from(payment);
    }

    /**
     * Transitions payment status from PENDING to PROCESSING.
     *
     * @param paymentId The unique identifier of the payment.
     */
    @Transactional
    public void transitionToProcessing(UUID paymentId) {
        log.info("Transitioning payment ID {} to PROCESSING...", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("Cannot transition payment ID {} to PROCESSING from status: {}", paymentId, payment.getStatus());
            throw new InvalidPaymentStateException("Payment must be in PENDING state to transition to PROCESSING.");
        }

        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);
        log.info("Payment ID {} successfully transitioned to PROCESSING", paymentId);
    }

    /**
     * Processes payment success: Locks sender wallet, debits funds, writes Transaction audit records,
     * and marks payment status as SUCCESS.
     *
     * @param paymentId The unique identifier of the payment.
     */
    @Transactional
    public void processSuccess(UUID paymentId) {
        log.info("Processing payment SUCCESS for payment ID: {}", paymentId);
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + paymentId));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Payment ID {} is already SUCCESS. Idempotent no-op.", paymentId);
            return;
        }

        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.PROCESSING) {
            log.warn("Cannot complete payment ID {} from status: {}", paymentId, payment.getStatus());
            throw new InvalidPaymentStateException("Payment must be PENDING or PROCESSING to complete successfully.");
        }

        // Lock and retrieve sender's wallet to ensure transaction isolation
        Wallet wallet = walletRepository.findByUserAndCurrency(payment.getUser(), payment.getCurrency())
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for currency " + payment.getCurrency()));

        Wallet lockedWallet = walletRepository.findByIdWithLock(wallet.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Failed to lock wallet for transaction."));

        if (lockedWallet.getBalance().compareTo(payment.getAmount()) < 0) {
            log.warn("Balance check failed at processing for payment ID: {} (Async debit failed due to insufficient funds)", paymentId);
            processFailed(paymentId, "Insufficient funds at time of processing");
            return;
        }

        // Perform balance deduction
        BigDecimal newBalance = lockedWallet.getBalance().subtract(payment.getAmount());
        lockedWallet.setBalance(newBalance);
        walletRepository.save(lockedWallet);

        // Generate custom transaction reference
        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Transaction tx = Transaction.create(
            lockedWallet,
            TransactionType.DEBIT,
            payment.getAmount(),
            newBalance,
            reference,
            "Async payment completion (M-Pesa simulation)",
            payment.getIdempotencyKey()
        );
        transactionRepository.save(tx);

        // Finalize state transition
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
        log.info("Payment ID {} successfully completed (status: SUCCESS, ref: {})", paymentId, reference);
    }

    /**
     * Processes payment failure: Transitions state to FAILED and records the failure reason.
     *
     * @param paymentId The unique identifier of the payment.
     * @param reason    The explanation for the transaction failure.
     */
    @Transactional
    public void processFailed(UUID paymentId, String reason) {
        log.info("Processing payment FAILURE for payment ID: {} (Reason: {})", paymentId, reason);
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + paymentId));

        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Payment ID {} is already FAILED. Idempotent no-op.", paymentId);
            return;
        }

        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.EXPIRED) {
            log.warn("Cannot mark payment ID {} as FAILED from status: {}", paymentId, payment.getStatus());
            throw new InvalidPaymentStateException("Cannot fail a payment that is already in SUCCESS or EXPIRED state.");
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        paymentRepository.save(payment);
        log.info("Payment ID {} successfully failed (status: FAILED)", paymentId);
    }

    /**
     * Retrieves the status of a specific payment request, ensuring the user owns the record.
     *
     * @param id   The unique ID of the payment.
     * @param user The authenticated user querying the status.
     * @return PaymentResponse containing current status.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentStatus(UUID id, User user) {
        log.info("Fetching payment status for ID: {} requested by user: {}", id, user.getEmail());
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found with ID: " + id));

        if (!payment.getUser().getId().equals(user.getId())) {
            log.warn("Unauthorized access attempt on payment ID {} by user {}", id, user.getEmail());
            throw new SecurityException("You do not have permission to view this payment transaction.");
        }

        return PaymentResponse.from(payment);
    }

    /**
     * Fetches a paginated history of all payments made by a user.
     *
     * @param user The authenticated user.
     * @param page Page index (0-based).
     * @param size Page size.
     * @return Paginated list of PaymentResponse DTOs.
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentHistory(User user, int page, int size) {
        log.info("Fetching paginated payment history for user {} (page: {}, size: {})", user.getEmail(), page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<Payment> payments = paymentRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return payments.map(PaymentResponse::from);
    }

    /**
     * Processes third-party gateway webhook status callbacks with signature verification.
     *
     * @param request CallbackRequest containing status details and payload signature.
     */
    @Transactional
    public void processCallback(CallbackRequest request) {
        log.info("Received third-party webhook callback for payment ID: {}", request.getPaymentId());

        // 1. Verify HMAC Signature
        if (!isValidSignature(request)) {
            log.warn("HMAC signature verification failed for payment ID: {}", request.getPaymentId());
            throw new SecurityException("Webhook signature verification failed.");
        }

        log.info("Webhook HMAC signature verified successfully for payment ID: {}", request.getPaymentId());

        // 2. Resolve Payment Status
        if ("SUCCESS".equalsIgnoreCase(request.getStatus())) {
            processSuccess(request.getPaymentId());
        } else {
            String reason = request.getFailureReason() != null ? request.getFailureReason() : "Webhook processor rejection";
            processFailed(request.getPaymentId(), reason);
        }
    }

    /**
     * Automatically transitions stuck PENDING or PROCESSING payments to EXPIRED after 10 minutes.
     */
    @Transactional
    public void expireStuckPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        List<Payment> stuckPayments = paymentRepository.findStuckPayments(
            List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING), 
            cutoff
        );

        if (!stuckPayments.isEmpty()) {
            log.info("Sweep found {} stuck payments created before {}. Transitioning to EXPIRED status...", stuckPayments.size(), cutoff);
            for (Payment payment : stuckPayments) {
                try {
                    payment.setStatus(PaymentStatus.EXPIRED);
                    payment.setFailureReason("Transaction expired: exceeded maximum processing time limit.");
                    paymentRepository.save(payment);
                    log.info("Successfully expired payment ID: {}", payment.getId());
                } catch (Exception e) {
                    log.error("Failed to expire payment ID: {}", payment.getId(), e);
                }
            }
        }
    }

    /**
     * Verifies the authenticity of a webhook signature using HMAC-SHA256.
     */
    private boolean isValidSignature(CallbackRequest request) {
        try {
            String dataToSign = request.getPaymentId().toString() + ":" + request.getStatus();
            if (request.getFailureReason() != null) {
                dataToSign += ":" + request.getFailureReason();
            }

            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);

            byte[] hashBytes = sha256HMAC.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().equalsIgnoreCase(request.getSignature());
        } catch (Exception e) {
            log.error("Error computing HMAC signature verification", e);
            return false;
        }
    }
}
