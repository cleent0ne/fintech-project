package com.cleentone.fintech.event;

import java.util.UUID;

/**
 * Event published when a payment is successfully registered as PENDING.
 * This event triggers asynchronous processing.
 *
 * @param paymentId The unique identifier of the payment.
 */
public record PaymentInitiatedEvent(UUID paymentId) {}
