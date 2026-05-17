package com.cleentone.fintech.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

import com.cleentone.fintech.model.enums.Currency;

/**
 * A request to add funds to a specific wallet.
 */
@Getter
@Setter
public class DepositRequest {

    @NotNull(message = "You must specify which currency you're depositing.")
    private Currency currency;  

    @NotNull(message = "Deposit amount is required.")
    @DecimalMin(value = "0.01", message = "You must deposit at least 0.01.")
    @DecimalMax(value = "1000000.00", message = "Deposits are capped at 1,000,000 per transaction.")
    @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places.")
    private BigDecimal amount;
}