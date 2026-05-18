package com.cleentone.fintech.exception;

/**
 * Thrown when an idempotency key conflict occurs due to mismatching request details.
 */
public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException(String message) {
        super(message);
    }
}
