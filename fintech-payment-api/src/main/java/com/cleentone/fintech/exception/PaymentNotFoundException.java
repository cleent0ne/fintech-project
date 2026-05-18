package com.cleentone.fintech.exception;

/**
 * Thrown when a payment query matches no records in the database.
 */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}
