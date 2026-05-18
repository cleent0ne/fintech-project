package com.cleentone.fintech.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Encapsulates the publishing of payment-related domain events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Publishes a PaymentInitiatedEvent for async processing.
     *
     * @param paymentId The unique identifier of the payment.
     */
    public void publishPaymentInitiated(UUID paymentId) {
        log.info("Broadcasting PaymentInitiatedEvent for payment ID: {}", paymentId);
        eventPublisher.publishEvent(new PaymentInitiatedEvent(paymentId));
    }
}
