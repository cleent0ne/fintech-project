package com.cleentone.fintech.exception;

/**
 * Thrown when a user tries to transfer or spend more money than they have in their wallet.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
