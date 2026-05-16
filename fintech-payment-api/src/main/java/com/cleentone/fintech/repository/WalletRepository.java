package com.cleentone.fintech.repository;

import com.cleentone.fintech.model.User;
import com.cleentone.fintech.model.Wallet;
import com.cleentone.fintech.model.enums.Currency;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserAndCurrency(User user, Currency currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user = :user AND w.currency = :currency")
    Optional<Wallet> findByUserAndCurrencyWithLock(@Param("user") User user, @Param("currency") Currency currency);

    List<Wallet> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") UUID id);

    // Used in AuthService to check if wallets already exist for a user
    // Prevents duplicate wallet creation on retry
    boolean existsByUserAndCurrency(User user, Currency currency);
}