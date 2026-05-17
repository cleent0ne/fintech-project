package com.cleentone.fintech.exception;

/**
 * A generic exception used when we can't find a requested resource (like a user or a wallet).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
