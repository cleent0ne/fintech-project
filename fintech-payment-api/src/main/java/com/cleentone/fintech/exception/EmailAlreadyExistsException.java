package com.cleentone.fintech.exception;

/**
 * Thrown when someone tries to register with an email that we already have in our database.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
