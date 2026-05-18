package com.cleentone.fintech.model.enums;

/**
 * Represents the lifecycle stages of an asynchronous payment transaction (e.g., M-Pesa).
 */
public enum PaymentStatus {
    /** Payment has been initiated by the client and is awaiting processing. */
    PENDING,

    /** The payment processor is currently validating and executing the request asynchronously. */
    PROCESSING,

    /** The payment completed successfully, wallets have been updated, and ledger records created. */
    SUCCESS,

    /** The payment failed (e.g. processor rejection, webhook failure, etc.). */
    FAILED,

    /** The payment processing timed out or was stuck and has been marked as expired. */
    EXPIRED
}
