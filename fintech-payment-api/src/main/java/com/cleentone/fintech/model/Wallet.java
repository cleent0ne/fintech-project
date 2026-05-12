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
 * A wallet holds a user's balance in one specific currency.
 *
 * Design decisions:
 *
 * 1. One wallet per user per currency (enforced by @UniqueConstraint)
 *    A user has a KES wallet AND a USD wallet — never two KES wallets.
 *
 * 2. BigDecimal(19,4) for balance — NEVER Double or Float
 *    Floating point cannot represent 0.1 exactly. In money, that matters.
 *    Precision 19 = handles any realistic monetary value.
 *    Scale 4 = covers KWD (3 decimal places) and future currencies.
 *
 * 3. @Version for optimistic locking
 *    Hibernate increments this on every UPDATE.
 *    If two transactions read version=5 and both try to write version=6,
 *    the second one finds version is already 6 — throws OptimisticLockException.
 *    Deposit operations use this. Transfer operations use SELECT FOR UPDATE instead.
 *
 * 4. balance defaults to ZERO — never null
 *    A null balance would require null checks everywhere money is calculated.
 *    Zero is always safe to add to or subtract from.
 */
@Entity
@Table(
    name = "wallets",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_wallet_user_currency",
        columnNames = {"user_id", "currency"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // LAZY fetch — we don't need full User object every time we load a wallet
    // Use user_id for ownership checks, only load User when you need their details
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    // DECIMAL(19,4) in the database — exact decimal arithmetic
    // Never use Float or Double for monetary values
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    // Optimistic locking — Hibernate manages this automatically
    // You never set this field manually
    @Version
    private Integer version;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Useful for auditing — "when was this wallet last used?"
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Convenience constructor for wallet creation at registration
    public Wallet(User user, Currency currency) {
        this.user = user;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
    }
}