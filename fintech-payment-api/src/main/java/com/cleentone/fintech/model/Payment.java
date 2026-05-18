package com.cleentone.fintech.model;

import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.model.enums.PaymentStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an asynchronous payment transaction (e.g. M-Pesa push / simulation).
 * It enforces state-machine integrity and uses optimistic locking to prevent race conditions.
 */
@Entity
@Table(
    name = "payments",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_idempotency",
        columnNames = {"idempotencyKey"}
    ),
    indexes = {
        @Index(name = "idx_payment_idempotency", columnList = "idempotencyKey"),
        @Index(name = "idx_payment_user", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The user who initiated the payment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PaymentStatus status = PaymentStatus.PENDING;

    // Idempotency key to ensure no duplicate payments are processed
    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    // The reason for failure if status is FAILED
    @Column(nullable = true)
    private String failureReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Optimistic locking for thread-safe state transition validation
    @Version
    private Long version;

    public Payment(User user, BigDecimal amount, Currency currency, String idempotencyKey) {
        this.user = user;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.PENDING;
    }
}
