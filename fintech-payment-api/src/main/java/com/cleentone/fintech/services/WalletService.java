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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    // ─── WALLET CREATION ─────────────────────────────────────────────────────

    /**
     * Creates one wallet per currency for a newly registered user.
     * Called by AuthService.register() — not exposed as an API endpoint.
     *
     * Uses existsByUserAndCurrency() to prevent duplicates on retry.
     */
    @Transactional
    public void createDefaultWallets(User user) {
        for (Currency currency : Currency.values()) {
            // Idempotent — safe to call multiple times (registration retry)
            if (!walletRepository.existsByUserAndCurrency(user, currency)) {
                walletRepository.save(new Wallet(user, currency));
            }
        }
        log.info("Created default wallets for user: {}", user.getId());
    }

    // ─── BALANCE QUERIES ─────────────────────────────────────────────────────

    /**
     * Returns all wallets for the authenticated user.
     * GET /wallet/balances
     */
    public List<WalletResponse> getAllWallets(User currentUser) {
        return walletRepository.findByUser(currentUser)
                .stream()
                .map(WalletResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Returns one specific wallet.
     * GET /wallet/{currency}/balance
     */
    public WalletResponse getWallet(User currentUser, Currency currency) {
        Wallet wallet = findWalletOrThrow(currentUser, currency);
        return WalletResponse.from(wallet);
    }

    // ─── DEPOSIT ─────────────────────────────────────────────────────────────

    /**
     * Adds funds to a wallet.
     * POST /wallet/deposit
     *
     * @Transactional — balance update and transaction record are one atomic operation.
     * If the transaction record creation fails, the balance update rolls back.
     */
    @Transactional
    public WalletResponse deposit(User currentUser, DepositRequest request) {
        Wallet wallet = findWalletOrThrow(currentUser, request.getCurrency());

        // Add to balance — BigDecimal.add() returns a new object, never mutates
        BigDecimal newBalance = wallet.getBalance().add(request.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // Create immutable record of this deposit
        String reference = UUID.randomUUID().toString();
        Transaction tx = Transaction.create(
                wallet,
                TransactionType.CREDIT,
                request.getAmount(),
                newBalance,
                reference,
                "Deposit"
        );
        transactionRepository.save(tx);

        log.info("Deposit: {} {} to wallet {} (ref: {})",
                request.getAmount(), request.getCurrency(), wallet.getId(), reference);

        return WalletResponse.from(wallet);
    }

    // ─── TRANSFER ────────────────────────────────────────────────────────────

    /**
     * Transfers money between two users' wallets.
     * POST /wallet/transfer
     *
     * This is the most critical method in the system.
     * Read every line carefully — each one prevents a specific failure mode.
     *
     * @Transactional — the entire operation is atomic.
     * A crash at any point rolls back all changes.
     */
    @Transactional
    public TransferResponse transfer(User currentUser, TransferRequest request) {

        // ── 1. Resolve sender wallet ─────────────────────────────────────────
        Wallet senderWallet = findWalletOrThrow(currentUser, request.getCurrency());

        // ── 2. Resolve receiver ──────────────────────────────────────────────
        // Use ResourceNotFoundException — don't reveal whether email is registered
        // (same user enumeration principle as auth)
        User receiverUser = userRepository
                .findByEmail(request.getReceiverEmail().toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recipient not found"));

        Wallet receiverWallet = walletRepository
                .findByUserAndCurrency(receiverUser, request.getCurrency())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Recipient does not have a " + request.getCurrency() + " wallet"));

        // ── 3. Business rule checks BEFORE acquiring locks ───────────────────
        // Do these before locking to fail fast without holding DB resources

        // Cannot send money to yourself
        if (senderWallet.getId().equals(receiverWallet.getId())) {
            throw new InvalidTransferException("Cannot transfer to yourself");
        }

        // Currencies must match — no cross-currency transfers in week 3
        if (!senderWallet.getCurrency().equals(receiverWallet.getCurrency())) {
            throw new InvalidTransferException("Cross-currency transfers are not supported");
        }

        // ── 4. Acquire locks in consistent order — prevents deadlock ─────────
        // If Thread A locks wallet-1 then waits for wallet-2,
        // and Thread B locks wallet-2 then waits for wallet-1 = DEADLOCK.
        // Solution: both threads always lock the lower UUID first.
        // Thread B will wait for Thread A to release wallet-1 before proceeding.
        UUID id1 = senderWallet.getId();
        UUID id2 = receiverWallet.getId();
        boolean senderIsFirst = id1.compareTo(id2) < 0;

        Wallet firstLock  = walletRepository.findByIdWithLock(senderIsFirst ? id1 : id2)
                .orElseThrow();
        Wallet secondLock = walletRepository.findByIdWithLock(senderIsFirst ? id2 : id1)
                .orElseThrow();

        // Re-assign to named variables for clarity
        Wallet lockedSender   = firstLock.getId().equals(id1) ? firstLock : secondLock;
        Wallet lockedReceiver = firstLock.getId().equals(id2) ? firstLock : secondLock;

        // ── 5. Check balance AFTER acquiring lock ────────────────────────────
        // Must check balance after locking — not before.
        // Checking before and locking after creates a TOCTOU vulnerability:
        // (Time Of Check / Time Of Use — balance could change between check and lock)
        if (lockedSender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + lockedSender.getCurrency()
                    + " " + lockedSender.getBalance());
        }

        // ── 6. Update balances ───────────────────────────────────────────────
        BigDecimal senderNewBalance   = lockedSender.getBalance().subtract(request.getAmount());
        BigDecimal receiverNewBalance = lockedReceiver.getBalance().add(request.getAmount());

        lockedSender.setBalance(senderNewBalance);
        lockedReceiver.setBalance(receiverNewBalance);

        walletRepository.save(lockedSender);
        walletRepository.save(lockedReceiver);

        // ── 7. Create linked transaction records ─────────────────────────────
        // Two records — one for each side of the transfer
        // They reference each other via relatedTransaction
        String senderRef   = UUID.randomUUID().toString();
        String receiverRef = UUID.randomUUID().toString();

        String transferDescription = buildDescription(request, currentUser, receiverUser);

        Transaction debitTx = Transaction.create(
                lockedSender,
                TransactionType.DEBIT,
                request.getAmount(),
                senderNewBalance,
                senderRef,
                "Transfer to " + receiverUser.getEmail()
        );

        Transaction creditTx = Transaction.create(
                lockedReceiver,
                TransactionType.CREDIT,
                request.getAmount(),
                receiverNewBalance,
                receiverRef,
                "Transfer from " + currentUser.getEmail()
                + (request.getDescription() != null ? ": " + request.getDescription() : "")
        );

        // Save both first, then link — both must exist before we can reference each other
        transactionRepository.save(debitTx);
        transactionRepository.save(creditTx);

        // Now link them — each points to the other
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

    // ─── TRANSACTION HISTORY ─────────────────────────────────────────────────

    /**
     * Returns paginated transaction history for one wallet.
     * GET /wallet/{currency}/transactions
     *
     * Always paginated — never return all transactions unbounded.
     * A wallet with 100,000 transactions would crash the server without pagination.
     */
    public Page<TransactionResponse> getTransactionHistory(
            User currentUser,
            Currency currency,
            int page,
            int size) {

        // Clamp page size — prevent client requesting 10,000 records per page
        int safeSize = Math.min(size, 100);

        Wallet wallet = findWalletOrThrow(currentUser, currency);

        PageRequest pageRequest = PageRequest.of(
                page,
                safeSize,
                Sort.by("createdAt").descending()  // newest first
        );

        return transactionRepository
                .findByWallet(wallet, pageRequest)
                .map(TransactionResponse::from);
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private Wallet findWalletOrThrow(User user, Currency currency) {
        return walletRepository.findByUserAndCurrency(user, currency)
                .orElseThrow(() -> new ResourceNotFoundException(
                        currency + " wallet not found"));
    }

    private String buildDescription(TransferRequest request, User sender, User receiver) {
        String base = "Transfer from " + sender.getEmail() + " to " + receiver.getEmail();
        return request.getDescription() != null
                ? base + ": " + request.getDescription()
                : base;
    }
}