package com.cleentone.fintech.dto;

import com.cleentone.fintech.model.Wallet;
import com.cleentone.fintech.model.enums.Currency;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class WalletResponse {

    private UUID id;
    private Currency currency;

    
    private BigDecimal balance;

    @JsonProperty("formatted_balance")
    private String formattedBalance;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static WalletResponse from(Wallet wallet) {
        WalletResponse response = new WalletResponse();
        response.id = wallet.getId();
        response.currency = wallet.getCurrency();

        BigDecimal displayBalance = wallet.getBalance()
                .setScale(2, RoundingMode.HALF_UP);

        response.balance = displayBalance;
        response.formattedBalance = formatBalance(wallet.getCurrency(), displayBalance);
        response.createdAt = wallet.getCreatedAt();
        response.updatedAt = wallet.getUpdatedAt();
        return response;
    }

    private static String formatBalance(Currency currency, BigDecimal amount) {
        // Format based on currency conventions
        return switch (currency) {
            case KES -> "KES " + String.format("%,.2f", amount);
            case USD -> "$ "  + String.format("%,.2f", amount);
        };
    }
}