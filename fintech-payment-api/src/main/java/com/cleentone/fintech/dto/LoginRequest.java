package com.cleentone.fintech.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A simple container for the data needed to log a user in.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "We need your email to log you in.")
    @Email(message = "That doesn't look like a valid email address.")
    @Size(max = 255, message = "Email is a bit too long (max 255 characters).")
    private String email;

    @NotBlank(message = "Password cannot be empty.")
    @Size(min = 6, max = 72, message = "Passwords must be between 6 and 72 characters.")
    private String password;
}
