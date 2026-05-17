package com.cleentone.fintech.services;

import com.cleentone.fintech.dto.*;
import com.cleentone.fintech.exception.InsufficientFundsException;
import com.cleentone.fintech.exception.InvalidTransferException;
import com.cleentone.fintech.exception.ResourceNotFoundException;
import com.cleentone.fintech.model.*;
import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.model.enums.TransactionType;
import com.cleentone.fintech.repository.TransactionRepository;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

/**
 * This is the heart of our payment system. It handles everything from 
 * creating wallets for new users to processing complex, thread-safe transfers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    /**
     * When a new user joins, we automatically set them up with wallets for 
     * every currency we support (currently KES and USD). 
     * We start them all at zero.
     */
    @Transactional
    public void createDefaultWallets(User user) {
        for (Currency currency : Currency.values()) {
            // We check if the wallet already exists just in case a registration 
            // is retried, avoiding duplicate wallet errors.
            if (!walletRepository.existsByUserAndCurrency(user, currency)) {
                walletRepository.save(new Wallet(user, currency));
            }
        }
        log.info("Created default wallets for user: {}", user.getId());
    }

    /**
     * Fetches all wallets belonging to the current user.
     */
    public List<WalletResponse> getAllWallets(User currentUser) {
        return walletRepository.findByUser(currentUser)
                .stream()
                .map(WalletResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Fetches a specific wallet by its currency.
     */
    public WalletResponse getWallet(User currentUser, Currency currency) {
        Wallet wallet = findWalletOrThrow(currentUser, currency);
        return WalletResponse.from(wallet);
    }

    /**
     * Handles adding money to a user's wallet.
     * We use @Transactional to ensure that both the balance update and 
     * the transaction audit log are saved together—all or nothing.
     */
    @Transactional
    public WalletResponse deposit(User currentUser, DepositRequest request) {
        // Basic check to make sure nobody tries to deposit zero or negative money.
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Deposit amount must be greater than zero");
        }

        // We lock the wallet during the update to prevent any race conditions.
        Wallet wallet = findWalletOrThrowWithLock(currentUser, request.getCurrency());

        BigDecimal newBalance = wallet.getBalance().add(request.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // Record the deposit in the audit log.
        String reference = UUID.randomUUID().toString();
        Transaction tx = Transaction.create(
                wallet,
                TransactionType.CREDIT,
                request.getAmount(),
                newBalance,
                reference,
                "Deposit",
                null
        );
        transactionRepository.save(tx);

        log.info("Deposit: {} {} to wallet {} (ref: {})",
                request.getAmount(), request.getCurrency(), wallet.getId(), reference);

        return WalletResponse.from(wallet);
    }

    /**
     * This is the most critical part of the code: moving money from one user to another.
     * It's wrapped in a @Transactional to ensure atomic success or failure.
     * 
     * We've implemented several layers of protection here:
     * 1. Idempotency checks to prevent double-spending on retries.
     * 2. Pessimistic locking to handle high-concurrency environments.
     * 3. Consistent lock ordering to prevent deadlocks.
     */
    @Transactional
    public TransferResponse transfer(User currentUser, TransferRequest request) {

        // Validate the amount first.
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be greater than zero");
        }

        // You shouldn't be able to send money to yourself.
        if (currentUser.getEmail().equalsIgnoreCase(request.getReceiverEmail().trim())) {
            throw new InvalidTransferException("Cannot transfer to yourself");
        }

        Wallet senderWallet = findWalletOrThrow(currentUser, request.getCurrency());

        // IDEMPOTENCY CHECK:
        // We check if this specific request (by its ID) has already been processed.
        // If it has, we just return the previous result instead of doing it again.
        Optional<Transaction> existingTx = transactionRepository
                .findByWalletAndIdempotencyKey(senderWallet, request.getRequestId());

        if (existingTx.isPresent()) {
            Transaction tx = existingTx.get();
            log.info("Duplicate request detected for requestId: {}. Returning existing transaction.", request.getRequestId());
            return new TransferResponse(
                    "Transfer successful (Duplicate)",
                    senderWallet.getCurrency(),
                    tx.getAmount().abs(),
                    tx.getBalanceAfter(),
                    request.getReceiverEmail(),
                    tx.getReference()
            );
        }

        // Look up the receiver. We use a generic error message if not found 
        // to avoid leaking whether a specific email exists in our system.
        User receiverUser = userRepository
                .findByEmail(request.getReceiverEmail().toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recipient not found"));

        Wallet receiverWallet = walletRepository
                .findByUserAndCurrency(receiverUser, request.getCurrency())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recipient does not have a " + request.getCurrency() + " wallet"));

        // Basic check for currency mismatch (though unlikely given the lookup logic).
        if (!senderWallet.getCurrency().equals(request.getCurrency())) {
            throw new InvalidTransferException("Currency mismatch in transfer request");
        }

        // DEADLOCK PREVENTION:
        // When locking two resources, always lock them in the same order (sorted by ID).
        // This ensures two concurrent transfers between the same people don't get stuck.
        UUID id1 = senderWallet.getId();
        UUID id2 = receiverWallet.getId();
        boolean senderIsFirst = id1.compareTo(id2) < 0;

        Wallet firstLock  = walletRepository.findByIdWithLock(senderIsFirst ? id1 : id2)
                .orElseThrow();
        entityManager.refresh(firstLock);
        
        Wallet secondLock = walletRepository.findByIdWithLock(senderIsFirst ? id2 : id1)
                .orElseThrow();
        entityManager.refresh(secondLock);

        Wallet lockedSender   = firstLock.getId().equals(id1) ? firstLock : secondLock;
        Wallet lockedReceiver = firstLock.getId().equals(id2) ? firstLock : secondLock;

        // Final balance check after we've successfully locked both wallets.
        if (lockedSender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + lockedSender.getCurrency()
                    + " " + lockedSender.getBalance());
        }

        // Update the balances.
        BigDecimal senderNewBalance   = lockedSender.getBalance().subtract(request.getAmount());
        BigDecimal receiverNewBalance = lockedReceiver.getBalance().add(request.getAmount());

        lockedSender.setBalance(senderNewBalance);
        lockedReceiver.setBalance(receiverNewBalance);

        walletRepository.save(lockedSender);
        walletRepository.save(lockedReceiver);

        // Create transaction records for both sides of the transfer.
        String senderRef   = UUID.randomUUID().toString();
        String receiverRef = UUID.randomUUID().toString();

        Transaction debitTx = Transaction.create(
                lockedSender,
                TransactionType.DEBIT,
                request.getAmount(),
                senderNewBalance,
                senderRef,
                "Transfer to " + receiverUser.getEmail(),
                request.getRequestId()
        );

        Transaction creditTx = Transaction.create(
                lockedReceiver,
                TransactionType.CREDIT,
                request.getAmount(),
                receiverNewBalance,
                receiverRef,
                "Transfer from " + currentUser.getEmail()
                + (request.getDescription() != null ? ": " + request.getDescription() : ""),
                request.getRequestId()
        );

        // Link the two transactions together for auditing.
        debitTx.setRelatedTransaction(creditTx);
        creditTx.setRelatedTransaction(debitTx);

        transactionRepository.save(debitTx);
        transactionRepository.save(creditTx);

        log.info("Transfer: {} {} from wallet {} to wallet {} (ref: {})",
                request.getAmount(), request.getCurrency(),
                lockedSender.getId(), lockedReceiver.getId(), senderRef);

        return new TransferResponse(
                "Transfer successful",
                request.getCurrency(),
                request.getAmount(),
                senderNewBalance,
                receiverUser.getEmail(),
                senderRef
        );
    }

    /**
     * Fetches a history of all transactions for a wallet.
     * We always use pagination here because returning thousands of 
     * transactions in one go could easily overwhelm the system.
     */
    public Page<TransactionResponse> getTransactionHistory(
            User currentUser,
            Currency currency,
            int page,
            int size) {

        // We limit the page size to 100 for safety.
        int safeSize = Math.min(size, 100);

        Wallet wallet = findWalletOrThrow(currentUser, currency);

        PageRequest pageRequest = PageRequest.of(
                page,
                safeSize,
                Sort.by("createdAt").descending()
        );

        return transactionRepository
                .findByWallet(wallet, pageRequest)
                .map(TransactionResponse::from);
    }

    // Helper methods for looking up wallets.
    
    private Wallet findWalletOrThrow(User user, Currency currency) {
        return walletRepository.findByUserAndCurrency(user, currency)
                .orElseThrow(() -> new ResourceNotFoundException(
                        currency + " wallet not found"));
    }

    private Wallet findWalletOrThrowWithLock(User user, Currency currency) {
        return walletRepository.findByUserAndCurrencyWithLock(user, currency)
                .orElseThrow(() -> new ResourceNotFoundException(
                        currency + " wallet not found"));
    }
}