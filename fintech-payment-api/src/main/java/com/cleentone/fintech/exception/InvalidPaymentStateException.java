package com.cleentone.fintech.exception;

/**
 * Thrown when an illegal state transition is attempted on a payment.
 */
public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
