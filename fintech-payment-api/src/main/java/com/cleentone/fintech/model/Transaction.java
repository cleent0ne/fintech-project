package com.cleentone.fintech.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.cleentone.fintech.model.enums.TransactionStatus;
import com.cleentone.fintech.model.enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An immutable record of every movement of money in the system.
 * Whether it's a deposit or a transfer, we capture it here for history and auditing.
 */
@Entity
@Table(
    name = "transactions",
    indexes = {
        @Index(name = "idx_tx_wallet_created", columnList = "wallet_id, created_at DESC"),
        @Index(name = "idx_tx_reference", columnList = "reference")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Every transaction belongs to exactly one wallet.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    // Storing the balance after the transaction helps us reconstruct the history
    // and verify the integrity of the wallet's current balance.
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    // A unique reference that we can share with the user or use for external tracking.
    @Column(unique = true, nullable = false)
    private String reference;

    // This key helps us prevent processing the same request twice.
    @Column(nullable = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private String description;

    // For transfers, we link the debit and credit sides together.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_transaction_id")
    private Transaction relatedTransaction;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * A factory method to quickly build a new Transaction record.
     */
    public static Transaction create(
            Wallet wallet,
            TransactionType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String reference,
            String description,
            String idempotencyKey) {
        Transaction tx = new Transaction();
        tx.wallet = wallet;
        tx.type = type;
        tx.amount = amount;
        tx.balanceAfter = balanceAfter;
        tx.reference = reference;
        tx.status = TransactionStatus.COMPLETED;
        tx.description = description;
        tx.idempotencyKey = idempotencyKey;
        return tx;
    }
}
