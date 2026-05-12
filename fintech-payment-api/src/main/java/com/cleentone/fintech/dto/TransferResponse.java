package com.cleentone.fintech.dto;

import com.cleentone.fintech.model.enums.Currency;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;


@Getter
@AllArgsConstructor
public class TransferResponse {

    private String message;
    private Currency currency;
    private BigDecimal amount;

    @JsonProperty("sender_new_balance")
    private BigDecimal senderNewBalance;

    @JsonProperty("receiver_email")
    private String receiverEmail;

    @JsonProperty("transaction_reference")
    private String transactionReference;  
}