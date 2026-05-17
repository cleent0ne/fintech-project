package com.cleentone.fintech.exception;

/**
 * Thrown during login if the email or password doesn't match our records.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
