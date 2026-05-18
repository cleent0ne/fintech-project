package com.cleentone.fintech.repository;

import com.cleentone.fintech.model.Payment;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.model.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing Payment entities.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Finds a payment record using its unique idempotency key.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Gets a paginated list of all payments initiated by a specific user.
     */
    Page<Payment> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * Finds payments that are stuck in a non-final status for longer than the cutoff limit.
     */
    @Query("SELECT p FROM Payment p WHERE p.status IN :statuses AND p.createdAt < :cutoff")
    List<Payment> findStuckPayments(
        @Param("statuses") List<PaymentStatus> statuses, 
        @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * Finds a specific payment record by its ID and locks it using PESSIMISTIC_WRITE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") UUID id);
}
