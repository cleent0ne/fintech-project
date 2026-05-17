package com.cleentone.fintech.model.enums;

/**
 * Distinguishes between money coming in and money going out.
 */
public enum TransactionType {
    CREDIT, // Money added to the wallet (e.g., a deposit).
    DEBIT   // Money removed from the wallet (e.g., a transfer out).
}
