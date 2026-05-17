package com.cleentone.fintech.repository;

import com.cleentone.fintech.model.Transaction;
import com.cleentone.fintech.model.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing our Transaction audit logs.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Finds all transactions for a specific wallet, with support for pagination.
     */
    Page<Transaction> findByWallet(Wallet wallet, Pageable pageable);

    /**
     * Checks if a transaction with a given reference already exists.
     */
    boolean existsByReference(String reference);

    /**
     * Looks up a specific transaction by its unique reference string.
     */
    Optional<Transaction> findByReference(String reference);

    /**
     * A critical check for idempotency: we see if a specific request (identified by its 
     * idempotencyKey) has already been processed for a wallet. This prevents 
     * double-spending if a client retries a request.
     */
    Optional<Transaction> findByWalletAndIdempotencyKey(Wallet wallet, String idempotencyKey);
}