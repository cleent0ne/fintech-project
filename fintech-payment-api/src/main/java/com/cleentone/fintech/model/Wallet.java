package com.cleentone.fintech.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.cleentone.fintech.model.enums.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A wallet holds a user's money in a specific currency.
 * 
 * Some quick design notes:
 * - We only allow one wallet per currency for each user.
 * - We use BigDecimal for the balance because using Double or Float with money 
 *   is a recipe for rounding disasters.
 * - We default the balance to zero to avoid annoying null checks.
 * - We use pessimistic locking in the service layer when updating balances 
 *   to keep everything safe and consistent even if multiple transactions 
 *   hit the same wallet at once.
 */
@Entity
@Table(
    name = "wallets",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_wallet_user_currency",
        columnNames = {"user_id", "currency"}
    ),
    indexes = {
        @Index(name = "idx_wallet_user_currency", columnList = "user_id, currency")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // We link the wallet to its owner. We use lazy loading here because 
    // we don't always need to fetch all the user's profile info just to check a balance.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    // Precision 19, scale 4 gives us plenty of room for large amounts and high precision.
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // This helps us see when the wallet was last active (deposited or transferred).
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Wallet(User user, Currency currency) {
        this.user = user;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
    }
}