package com.cleentone.fintech.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    
    private String token;
    private String email;
    private String message;
    private long expiresIn;
}
