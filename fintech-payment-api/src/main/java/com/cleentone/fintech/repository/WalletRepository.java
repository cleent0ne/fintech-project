package com.cleentone.fintech.repository;

import com.cleentone.fintech.model.User;
import com.cleentone.fintech.model.Wallet;
import com.cleentone.fintech.model.enums.Currency;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing Wallet entities.
 * We've included some custom queries here to handle pessimistic locking, 
 * which is critical for keeping our balances accurate during concurrent transfers.
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /**
     * Standard lookup for a user's wallet in a specific currency.
     */
    Optional<Wallet> findByUserAndCurrency(User user, Currency currency);

    /**
     * Finds a wallet and places a PESSIMISTIC_WRITE lock on it.
     * This stops other transactions from modifying the wallet until we're done.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user = :user AND w.currency = :currency")
    Optional<Wallet> findByUserAndCurrencyWithLock(@Param("user") User user, @Param("currency") Currency currency);

    /**
     * Gets all wallets belonging to a specific user.
     */
    @EntityGraph(attributePaths = {"user"})
    List<Wallet> findByUser(User user);

    /**
     * Finds a specific wallet by its ID and locks it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") UUID id);

    /**
     * A quick check to see if a user already has a wallet for a certain currency.
     * We use this during registration to avoid creating duplicates.
     */
    boolean existsByUserAndCurrency(User user, Currency currency);
}