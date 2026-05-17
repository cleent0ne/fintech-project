package com.cleentone.fintech.exception;

/**
 * Thrown if there's something wrong with a transfer request, like trying to send 
 * a negative amount or sending money to yourself.
 */
public class InvalidTransferException extends RuntimeException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
