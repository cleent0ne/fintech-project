package com.cleentone.fintech.controllers;

import com.cleentone.fintech.dto.*;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.services.WalletService;
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
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    /**
     * GET /wallet/balances
     * Returns all wallets for the authenticated user (one per currency)
     */
    @GetMapping("/balances")
    public ResponseEntity<List<WalletResponse>> getAllBalances(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(walletService.getAllWallets(currentUser));
    }

    /**
     * GET /wallet/{currency}/balance
     * Returns one specific wallet — e.g. GET /wallet/KES/balance
     */
    @GetMapping("/{currency}/balance")
    public ResponseEntity<WalletResponse> getBalance(
            @PathVariable Currency currency,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(walletService.getWallet(currentUser, currency));
    }

    /**
     * POST /wallet/deposit
     * Body: { "currency": "KES", "amount": 5000.00 }
     */
    @PostMapping("/deposit")
    public ResponseEntity<WalletResponse> deposit(
            @Valid @RequestBody DepositRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(walletService.deposit(currentUser, request));
    }

    /**
     * Body: { "receiverEmail": "...", "currency": "KES", "amount": 500.00, "description": "..." }
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = resolveUser(userDetails);
        return ResponseEntity.ok(walletService.transfer(currentUser, request));
    }

    /**
     * GET /wallet/{currency}/transactions?page=0&size=20
     * Paginated transaction history — most recent first
     */
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

    /**
     * Resolves the authenticated UserDetails back to the full User entity.
     *
     * @AuthenticationPrincipal gives us the Spring Security UserDetails (just email + roles).
     * We need the full User entity (id, fullName, etc.) for service calls.
     * This is a lightweight DB call — the user must exist if the token is valid.
     */
    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();
    }
}