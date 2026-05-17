package com.cleentone.fintech.dto;

import com.cleentone.fintech.validation.SafeString;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The data we require from a user when they're signing up for a new account.
 */
@Data
public class RegisterRequest {
    
    @NotBlank(message = "Email is required.")
    @Email(message = "That email doesn't look quite right.")
    @Size(max = 255, message = "Email is too long (max 255 characters).")
    private String email;

    @NotBlank(message = "You must provide a password.")
    @Size(min = 6, max = 72, message = "Passwords should be between 6 and 72 characters.")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$", 
             message = "Password needs at least one uppercase letter, one lowercase letter, one number, and one special character.")
    private String password;

    @NotBlank(message = "Please tell us your full name.")
    @Size(min = 2, max = 100, message = "Names should be between 2 and 100 characters.")
    @Pattern(regexp = "^[a-zA-Z\\s\\-'.]+$", message = "Names can only contain letters and basic punctuation.")
    @SafeString(message = "Full name contains invalid characters.")
    private String fullName;
}
