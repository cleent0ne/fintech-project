package com.cleentone.fintech.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

import com.cleentone.fintech.model.enums.Currency;

/**
 * Everything we need to know to process a peer-to-peer transfer.
 */
@Getter
@Setter
public class TransferRequest {

    @NotBlank(message = "We need to know who you're sending money to.")
    @Email(message = "That doesn't look like a valid email address.")
    private String receiverEmail;

    // We use this ID to make sure we don't process the same transfer twice.
    @NotBlank(message = "Request ID is required to prevent double-spending.")
    private String requestId;

    @NotNull(message = "Please specify the currency.")
    private Currency currency;

    @NotNull(message = "How much do you want to send?")
    @DecimalMin(value = "0.01", message = "The minimum transfer is 0.01.")
    @DecimalMax(value = "1000000.00", message = "You can't send more than 1,000,000 at a time.")
    @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places.")
    private BigDecimal amount;

    @Size(max = 200, message = "Keep your description under 200 characters.")
    private String description;
}