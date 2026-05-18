package com.cleentone.fintech.dto;

import com.cleentone.fintech.model.Payment;
import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.model.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents the response sent to the client when a payment is initiated or queried.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    @JsonProperty("payment_id")
    private UUID paymentId;

    private BigDecimal amount;
    private Currency currency;
    private PaymentStatus status;

    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    /**
     * Map a Payment entity to a PaymentResponse DTO.
     */
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus(),
            payment.getIdempotencyKey(),
            payment.getFailureReason(),
            payment.getCreatedAt()
        );
    }
}
