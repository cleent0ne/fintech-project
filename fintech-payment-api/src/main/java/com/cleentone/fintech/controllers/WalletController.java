package com.cleentone.fintech.controllers;

import com.cleentone.fintech.dto.*;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.services.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Wallet endpoints — all require authentication (no permitAll in SecurityConfig).
 *
 * Controllers are dumb:
 * - Receive request
 * - Resolve current user from SecurityContext
 * - Call WalletService
 * - Return response
 *
 * Zero business logic here.
 */
@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    @Operation(summary = "Get all balances", description = "Returns all wallets and their balances for the authenticated user.")
    @GetMapping("/balances")
    public ResponseEntity<List<WalletResponse>> getAllBalances(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(walletService.getAllWallets(currentUser));
    }

    @Operation(summary = "Get specific wallet balance", description = "Returns the balance for a specific currency.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Wallet not found for the specified currency")
    })
    @GetMapping("/{currency}/balance")
    public ResponseEntity<WalletResponse> getBalance(
            @PathVariable Currency currency,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(walletService.getWallet(currentUser, currency));
    }

    @Operation(summary = "Deposit funds", description = "Adds a specified amount to the user's wallet.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Deposit successful"),
        @ApiResponse(responseCode = "400", description = "Invalid amount or currency")
    })
    @PostMapping("/deposit")
    public ResponseEntity<WalletResponse> deposit(
            @Valid @RequestBody DepositRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(walletService.deposit(currentUser, request));
    }

    @Operation(summary = "Transfer money", description = "Transfers funds between users of the same currency.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transfer successful"),
        @ApiResponse(responseCode = "400", description = "Insufficient funds, self-transfer, or invalid input"),
        @ApiResponse(responseCode = "404", description = "Recipient not found")
    })
    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(walletService.transfer(currentUser, request));
    }

    @Operation(summary = "Get transaction history", description = "Returns a paginated list of transactions for a specific wallet.")
    @GetMapping("/{currency}/transactions")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @PathVariable Currency currency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(
                walletService.getTransactionHistory(currentUser, currency, page, size));
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();
    }
}