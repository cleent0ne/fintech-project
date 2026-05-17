package com.cleentone.fintech.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Data returned after a successful login or registration. 
 * It includes the JWT token and some basic info for the client.
 */
@Data
@Builder
public class AuthResponse {
    
    private String token;
    private String email;
    private String message;
    
    // How long the token is valid for, in seconds.
    private long expiresIn;
}
