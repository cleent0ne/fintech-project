package com.cleentone.fintech.dto;

import com.cleentone.fintech.model.Wallet;
import com.cleentone.fintech.model.enums.Currency;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A snapshot of a user's wallet info, including a pretty-formatted balance.
 */
@Getter
public class WalletResponse {

    private UUID id;
    private Currency currency;

    private BigDecimal balance;

    // A human-readable balance string (e.g., "$ 1,234.56").
    @JsonProperty("formatted_balance")
    private String formattedBalance;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Maps a Wallet entity to this response object and handles balance formatting.
     */
    public static WalletResponse from(Wallet wallet) {
        WalletResponse response = new WalletResponse();
        response.id = wallet.getId();
        response.currency = wallet.getCurrency();

        // We round to 2 decimal places for display purposes.
        BigDecimal displayBalance = wallet.getBalance()
                .setScale(2, RoundingMode.HALF_UP);

        response.balance = displayBalance;
        response.formattedBalance = formatBalance(wallet.getCurrency(), displayBalance);
        response.createdAt = wallet.getCreatedAt();
        response.updatedAt = wallet.getUpdatedAt();
        return response;
    }

    /**
     * Adds the currency symbol or code to the balance string.
     */
    private static String formatBalance(Currency currency, BigDecimal amount) {
        return switch (currency) {
            case KES -> "KES " + String.format("%,.2f", amount);
            case USD -> "$ "  + String.format("%,.2f", amount);
        };
    }
}