package com.cleentone.fintech.scheduler;

import com.cleentone.fintech.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically sweeps the database to find and expire payments stuck in transition states.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryScheduler {

    private final PaymentService paymentService;

    /**
     * Periodically expires payments that have remained stuck in transient states (PENDING/PROCESSING)
     * for longer than 10 minutes. Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    public void sweepStuckPayments() {
        log.debug("Starting scheduled sweeper for stuck payments...");
        try {
            paymentService.expireStuckPayments();
        } catch (Exception e) {
            log.error("Error occurred during scheduled stuck payment sweep", e);
        }
    }
}
