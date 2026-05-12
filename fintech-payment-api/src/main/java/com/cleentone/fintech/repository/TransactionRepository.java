package com.cleentone.fintech.repository;

import com.cleentone.fintech.model.Transaction;
import com.cleentone.fintech.model.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    
    Page<Transaction> findByWallet(Wallet wallet, Pageable pageable);

    // Idempotency check — does a transaction with this reference already exist?
    boolean existsByReference(String reference);

    // Fetch a specific transaction by reference — for status lookups
    Optional<Transaction> findByReference(String reference);
}