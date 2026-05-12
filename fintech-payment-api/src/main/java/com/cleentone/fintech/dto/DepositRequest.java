package com.cleentone.fintech.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

import com.cleentone.fintech.model.enums.Currency;


@Getter
@Setter
public class DepositRequest {

    @NotNull(message = "Currency is required")
    private Currency currency;  


    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "1000000.00", message = "Amount cannot exceed 1,000,000 per transaction")
    @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
    private BigDecimal amount;
    
}