package com.cleentone.fintech.service;

import com.cleentone.fintech.dto.PaymentRequest;
import com.cleentone.fintech.dto.PaymentResponse;
import com.cleentone.fintech.event.PaymentEventPublisher;
import com.cleentone.fintech.exception.DuplicatePaymentException;
import com.cleentone.fintech.exception.InsufficientFundsException;
import com.cleentone.fintech.exception.InvalidPaymentStateException;
import com.cleentone.fintech.exception.ResourceNotFoundException;
import com.cleentone.fintech.model.Payment;
import com.cleentone.fintech.model.User;
import com.cleentone.fintech.model.Wallet;
import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.model.enums.PaymentStatus;
import com.cleentone.fintech.repository.PaymentRepository;
import com.cleentone.fintech.repository.TransactionRepository;
import com.cleentone.fintech.repository.WalletRepository;
import com.cleentone.fintech.services.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("sender@example.com");

        wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setUser(user);
        wallet.setCurrency(Currency.KES);
        wallet.setBalance(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("initiate succeeds, persists PENDING state and broadcasts event")
    void initiate_success() {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("200.00"));
        request.setCurrency(Currency.KES);
        request.setIdempotencyKey("idemp-key-1");

        when(paymentRepository.findByIdempotencyKey("idemp-key-1")).thenReturn(Optional.empty());
        when(walletRepository.findByUserAndCurrency(user, Currency.KES)).thenReturn(Optional.of(wallet));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment saved = i.getArgument(0);
            setField(saved, "id", UUID.randomUUID());
            return saved;
        });

        PaymentResponse response = paymentService.initiate(user, request);
        assertNotNull(response);
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("200.00"), response.getAmount());

        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(paymentEventPublisher, times(1)).publishPaymentInitiated(any(UUID.class));
    }

    @Test
    @DisplayName("initiate with duplicate key and same details returns existing payment without publishing event")
    void initiate_duplicateKey_sameDetails() {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("200.00"));
        request.setCurrency(Currency.KES);
        request.setIdempotencyKey("idemp-key-1");

        Payment existing = new Payment(user, new BigDecimal("200.00"), Currency.KES, "idemp-key-1");
        setField(existing, "id", UUID.randomUUID());

        when(paymentRepository.findByIdempotencyKey("idemp-key-1")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.initiate(user, request);
        assertNotNull(response);
        assertEquals(existing.getId(), response.getPaymentId());

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(paymentEventPublisher, never()).publishPaymentInitiated(any(UUID.class));
    }

    @Test
    @DisplayName("initiate with duplicate key but different details throws DuplicatePaymentException")
    void initiate_duplicateKey_differentDetails() {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("300.00")); // different amount
        request.setCurrency(Currency.KES);
        request.setIdempotencyKey("idemp-key-1");

        Payment existing = new Payment(user, new BigDecimal("200.00"), Currency.KES, "idemp-key-1");
        setField(existing, "id", UUID.randomUUID());

        when(paymentRepository.findByIdempotencyKey("idemp-key-1")).thenReturn(Optional.of(existing));

        assertThrows(DuplicatePaymentException.class, () -> paymentService.initiate(user, request));
    }

    @Test
    @DisplayName("initiate with insufficient funds throws InsufficientFundsException")
    void initiate_insufficientFunds() {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("2000.00")); // more than 1000.00 wallet balance
        request.setCurrency(Currency.KES);
        request.setIdempotencyKey("idemp-key-1");

        when(paymentRepository.findByIdempotencyKey("idemp-key-1")).thenReturn(Optional.empty());
        when(walletRepository.findByUserAndCurrency(user, Currency.KES)).thenReturn(Optional.of(wallet));

        assertThrows(InsufficientFundsException.class, () -> paymentService.initiate(user, request));
    }

    @Test
    @DisplayName("transitionToProcessing transitions PENDING to PROCESSING status")
    void transitionToProcessing_success() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(user, new BigDecimal("200.00"), Currency.KES, "idemp-key-1");
        setField(payment, "id", paymentId);
        payment.setStatus(PaymentStatus.PENDING);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        paymentService.transitionToProcessing(paymentId);
        assertEquals(PaymentStatus.PROCESSING, payment.getStatus());
        verify(paymentRepository, times(1)).save(payment);
    }

    @Test
    @DisplayName("transitionToProcessing on non-PENDING payment throws InvalidPaymentStateException")
    void transitionToProcessing_invalidState() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(user, new BigDecimal("200.00"), Currency.KES, "idemp-key-1");
        setField(payment, "id", paymentId);
        payment.setStatus(PaymentStatus.SUCCESS); // already successful

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThrows(InvalidPaymentStateException.class, () -> paymentService.transitionToProcessing(paymentId));
    }

    @Test
    @DisplayName("processSuccess locked wallet debit success")
    void processSuccess_success() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(user, new BigDecimal("200.00"), Currency.KES, "idemp-key-1");
        setField(payment, "id", paymentId);
        payment.setStatus(PaymentStatus.PROCESSING);

        when(paymentRepository.findByIdWithLock(paymentId)).thenReturn(Optional.of(payment));
        when(walletRepository.findByUserAndCurrency(user, Currency.KES)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdWithLock(wallet.getId())).thenReturn(Optional.of(wallet));

        paymentService.processSuccess(paymentId);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(new BigDecimal("800.00"), wallet.getBalance());

        verify(walletRepository, times(1)).save(wallet);
        verify(transactionRepository, times(1)).save(any());
        verify(paymentRepository, times(1)).save(payment);
    }

    @Test
    @DisplayName("processFailed valid transition works")
    void processFailed_success() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(user, new BigDecimal("200.00"), Currency.KES, "idemp-key-1");
        setField(payment, "id", paymentId);
        payment.setStatus(PaymentStatus.PENDING);

        when(paymentRepository.findByIdWithLock(paymentId)).thenReturn(Optional.of(payment));

        paymentService.processFailed(paymentId, "simulated failure");

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("simulated failure", payment.getFailureReason());
        verify(paymentRepository, times(1)).save(payment);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
