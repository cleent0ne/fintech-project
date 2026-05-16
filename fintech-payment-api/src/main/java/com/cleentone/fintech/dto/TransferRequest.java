package com.cleentone.fintech.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

import com.cleentone.fintech.model.enums.Currency;


@Getter
@Setter
public class TransferRequest {

    @NotBlank(message = "Receiver email is required")
    @Email(message = "Receiver email must be a valid email address")
    private String receiverEmail;

    @NotBlank(message = "Request ID is required for idempotency")
    private String requestId;

    @NotNull(message = "Currency is required")
    private Currency currency;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Transfer amount must be at least 0.01")
    @DecimalMax(value = "1000000.00", message = "Transfer amount cannot exceed 1,000,000")
    @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
    private BigDecimal amount;

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;
}