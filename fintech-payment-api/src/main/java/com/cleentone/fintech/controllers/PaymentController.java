package com.cleentone.fintech.controllers;

import com.cleentone.fintech.dto.CallbackRequest;
import com.cleentone.fintech.dto.PaymentRequest;
import com.cleentone.fintech.dto.PaymentResponse;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.services.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Handles REST requests for the Asynchronous Payment Layer.
 * Supports secure client-initiated payments, queries, history logs, and payment gateway webhooks.
 */
@RestController
@RequestMapping("/api/v0/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    /**
     * Initiates a new asynchronous payment.
     */
    @Operation(summary = "Initiate an async payment", description = "Creates a PENDING async payment and publishes initiation event.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment successfully initiated"),
        @ApiResponse(responseCode = "400", description = "Invalid payload or insufficient funds"),
        @ApiResponse(responseCode = "409", description = "Duplicate payment idempotency conflict")
    })
    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponse> initiate(
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        PaymentResponse response = paymentService.initiate(currentUser, request);
        return ResponseEntity.status(202).body(response);
    }

    /**
     * Retrieves the status of a specific payment request.
     */
    @Operation(summary = "Get payment status", description = "Returns status of a specific payment if owned by user.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Unauthorized access attempt to payment record"),
        @ApiResponse(responseCode = "404", description = "Payment ID not found")
    })
    @GetMapping("/{id}/status")
    public ResponseEntity<PaymentResponse> getStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        PaymentResponse response = paymentService.getPaymentStatus(id, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Gets a paginated list of all payment attempts initiated by the user.
     */
    @Operation(summary = "Get payment history", description = "Returns a paginated list of all past payment requests.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/history")
    public ResponseEntity<Page<PaymentResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        Page<PaymentResponse> history = paymentService.getPaymentHistory(currentUser, page, size);
        return ResponseEntity.ok(history);
    }

    /**
     * Gateway webhook callback endpoint.
     * Accessible publicly (anonymous), validated using HMAC SHA256 signature verification.
     */
    @Operation(summary = "External gateway webhook callback", description = "Resolves payment success or failures via signature verification.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook callback processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid payload or signature validation failed"),
        @ApiResponse(responseCode = "404", description = "Associated payment ID not found")
    })
    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@Valid @RequestBody CallbackRequest request) {
        paymentService.processCallback(request);
        return ResponseEntity.ok().build();
    }

    /**
     * Resolves User entity from authentication details.
     */
    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user email not found in database."));
    }
}
