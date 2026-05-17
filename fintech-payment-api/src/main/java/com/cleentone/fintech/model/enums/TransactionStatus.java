package com.cleentone.fintech.model.enums;

/**
 * The possible states a transaction can be in.
 */
public enum TransactionStatus {
    PENDING,   // Transaction is created but not yet finalized.
    COMPLETED, // Money has been successfully moved.
    FAILED,    // Something went wrong and the transaction stopped.
    REVERSED   // The transaction was undone.
}
