package com.cleentone.fintech.dto;

import com.cleentone.fintech.model.Transaction;
import com.cleentone.fintech.model.enums.TransactionStatus;
import com.cleentone.fintech.model.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A user-friendly representation of a transaction record.
 */
@Getter
public class TransactionResponse {

    private UUID id;

    private TransactionType type;  

    private BigDecimal amount;

    private TransactionStatus status;

    private String description;

    private String reference;

    @JsonProperty("balance_after")
    private BigDecimal balanceAfter;

    // For transfers, this links to the transaction on the other person's side.
    @JsonProperty("related_transaction_id")
    private UUID relatedTransactionId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    /**
     * Converts a Transaction entity into this response object.
     */
    public static TransactionResponse from(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.id = transaction.getId();
        response.type = transaction.getType();
        response.amount = transaction.getAmount();
        response.status = transaction.getStatus();
        response.description = transaction.getDescription();
        response.reference = transaction.getReference();
        response.balanceAfter = transaction.getBalanceAfter();
        response.createdAt = transaction.getCreatedAt();

        if (transaction.getRelatedTransaction() != null) {
            response.relatedTransactionId = transaction.getRelatedTransaction().getId();
        }

        return response;
    }
}