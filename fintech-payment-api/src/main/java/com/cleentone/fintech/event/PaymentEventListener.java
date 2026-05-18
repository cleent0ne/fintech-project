package com.cleentone.fintech.event;

import com.cleentone.fintech.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Asynchronously listens to domain events and coordinates payment execution.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final PaymentService paymentService;

    /**
     * Listens to PaymentInitiatedEvent, simulates an external delay, and updates payment state.
     * Executes asynchronously on the paymentAsyncExecutor thread pool.
     *
     * @param event The PaymentInitiatedEvent containing the payment's UUID.
     */
    @Async("paymentAsyncExecutor")
    @EventListener
    public void handlePaymentInitiated(PaymentInitiatedEvent event) {
        UUID paymentId = event.paymentId();
        log.info("Received PaymentInitiatedEvent for payment ID: {} on thread {}", 
            paymentId, Thread.currentThread().getName());

        try {
            // 1. Transition the payment to the PROCESSING state
            paymentService.transitionToProcessing(paymentId);

            // 2. Simulate external M-Pesa processing delay (e.g. 3 seconds)
            log.info("Simulating external payment processor validation delay of 3 seconds...");
            Thread.sleep(3000);

            // 3. Simulate processing outcome (90% success rate / 10% failure rate)
            double outcome = Math.random();
            if (outcome < 0.90) {
                log.info("Simulated payment processor SUCCESS for payment ID: {}", paymentId);
                paymentService.processSuccess(paymentId);
            } else {
                log.warn("Simulated payment processor REJECTION for payment ID: {}", paymentId);
                paymentService.processFailed(paymentId, "Simulated third-party payment processor rejection");
            }

        } catch (InterruptedException e) {
            log.error("Asynchronous processing interrupted for payment ID: {}", paymentId, e);
            Thread.currentThread().interrupt();
            paymentService.processFailed(paymentId, "Async processing interrupted: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error occurred in async payment listener for payment ID: {}", paymentId, e);
            paymentService.processFailed(paymentId, "Processing exception: " + e.getMessage());
        }
    }
}
