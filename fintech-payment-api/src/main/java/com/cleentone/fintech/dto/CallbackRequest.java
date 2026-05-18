package com.cleentone.fintech.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Payload sent by external payment processors to complete or fail a payment.
 */
@Getter
@Setter
public class CallbackRequest {

    @NotNull(message = "Payment ID is required.")
    @JsonProperty("payment_id")
    private UUID paymentId;

    @NotBlank(message = "Status is required.")
    private String status;

    @JsonProperty("failure_reason")
    private String failureReason;

    @NotBlank(message = "HMAC Signature is required for security verification.")
    private String signature;
}
