package com.cleentone.fintech.dto;

import com.cleentone.fintech.model.enums.Currency;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Payload carried to initiate a new asynchronous payment.
 */
@Getter
@Setter
public class PaymentRequest {

    @NotNull(message = "Please specify the payment amount.")
    @DecimalMin(value = "0.01", message = "The minimum payment is 0.01.")
    @DecimalMax(value = "1000000.00", message = "You cannot process more than 1,000,000 at a time.")
    @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places.")
    private BigDecimal amount;

    @NotNull(message = "Please specify the currency.")
    private Currency currency;

    @NotBlank(message = "Idempotency key is required to prevent double charges.")
    @JsonProperty("idempotency_key")
    private String idempotencyKey;
}
