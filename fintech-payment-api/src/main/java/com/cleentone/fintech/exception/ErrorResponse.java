package com.cleentone.fintech.exception;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * This is the standard JSON structure we return whenever an error occurs.
 * It helps clients understand exactly what went wrong and when.
 */
@Data
@AllArgsConstructor
public class ErrorResponse {
    
    // A short code representing the error type (e.g., "VALIDATION_FAILED").
    private String error;
    
    // A human-readable message explaining the error.
    private String message;
    
    private LocalDateTime timestamp;

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}