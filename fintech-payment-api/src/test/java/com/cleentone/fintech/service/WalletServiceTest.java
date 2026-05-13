package com.cleentone.fintech.service;

import com.cleentone.fintech.dto.DepositRequest;
import com.cleentone.fintech.dto.TransferRequest;
import com.cleentone.fintech.dto.WalletResponse;
import com.cleentone.fintech.dto.TransferResponse;
import com.cleentone.fintech.exception.InsufficientFundsException;
import com.cleentone.fintech.exception.InvalidTransferException;
import com.cleentone.fintech.exception.ResourceNotFoundException;
import com.cleentone.fintech.model.*;
import com.cleentone.fintech.model.enums.TransactionStatus;
import com.cleentone.fintech.model.enums.TransactionType;
import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.repository.TransactionRepository;
import com.cleentone.fintech.repository.UserRepository;
import com.cleentone.fintech.repository.WalletRepository;
import com.cleentone.fintech.services.WalletService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService")
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private WalletService walletService;

    private User sender;
    private User receiver;
    private Wallet senderKesWallet;
    private Wallet receiverKesWallet;
    private Wallet senderUsdWallet;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setId(UUID.randomUUID());
        sender.setEmail("sender@test.com");

        receiver = new User();
        receiver.setId(UUID.randomUUID());
        receiver.setEmail("receiver@test.com");

        senderKesWallet = new Wallet(sender, Currency.KES);
        senderKesWallet.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        senderKesWallet.setBalance(new BigDecimal("1000.00"));

        receiverKesWallet = new Wallet(receiver, Currency.KES);
        receiverKesWallet.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        receiverKesWallet.setBalance(new BigDecimal("500.00"));

        senderUsdWallet = new Wallet(sender, Currency.USD);
        senderUsdWallet.setId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        senderUsdWallet.setBalance(new BigDecimal("100.00"));
    }

    // DEPOSIT


    @Nested
    @DisplayName("deposit()")
    class Deposit {

        @Test
        @DisplayName("should increase balance by the exact deposit amount")
        void deposit_success_updatesBalance() {
            // Arrange
            DepositRequest request = new DepositRequest();
            request.setCurrency(Currency.KES);
            request.setAmount(new BigDecimal("500.00"));

            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));
            when(walletRepository.save(any(Wallet.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            WalletResponse response = walletService.deposit(sender, request);

            // Assert — balance is exactly 1500.00 (1000 + 500)
            assertThat(response.getBalance())
                    .isEqualByComparingTo(new BigDecimal("1500.00"));
        }

        @Test
        @DisplayName("should create a CREDIT transaction record with correct balanceAfter")
        void deposit_createsTransactionRecord() {
            // Arrange
            DepositRequest request = new DepositRequest();
            request.setCurrency(Currency.KES);
            request.setAmount(new BigDecimal("200.00"));

            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));
            when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Capture what gets saved to transactionRepository
            ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
            when(transactionRepository.save(txCaptor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            walletService.deposit(sender, request);

            // Assert transaction record
            Transaction savedTx = txCaptor.getValue();
            assertThat(savedTx.getType()).isEqualTo(TransactionType.CREDIT);
            assertThat(savedTx.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
            // balanceAfter = 1000 + 200 = 1200
            assertThat(savedTx.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("1200.00"));
            assertThat(savedTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(savedTx.getReference()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when wallet not found")
        void deposit_walletNotFound_throwsException() {
            // Arrange
            DepositRequest request = new DepositRequest();
            request.setCurrency(Currency.USD);
            request.setAmount(new BigDecimal("100.00"));

            when(walletRepository.findByUserAndCurrency(sender, Currency.USD))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> walletService.deposit(sender, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            // No save should have been attempted
            verify(walletRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should generate a unique reference for every deposit")
        void deposit_generatesUniqueReferences() {
            // Arrange
            DepositRequest request = new DepositRequest();
            request.setCurrency(Currency.KES);
            request.setAmount(new BigDecimal("100.00"));

            // Fresh wallet for second deposit
            Wallet wallet2 = new Wallet(sender, Currency.KES);
            wallet2.setId(UUID.randomUUID());
            wallet2.setBalance(new BigDecimal("1000.00"));

            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet))
                    .thenReturn(Optional.of(wallet2));
            when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            when(transactionRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act — two deposits
            walletService.deposit(sender, request);
            walletService.deposit(sender, request);

            // Assert — two different references
            List<Transaction> saved = captor.getAllValues();
            assertThat(saved.get(0).getReference())
                    .isNotEqualTo(saved.get(1).getReference());
        }
    }

   
    // TRANSFER
   

    @Nested
    @DisplayName("transfer()")
    class Transfer {

        private TransferRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new TransferRequest();
            validRequest.setReceiverEmail("receiver@test.com");
            validRequest.setCurrency(Currency.KES);
            validRequest.setAmount(new BigDecimal("300.00"));
            validRequest.setDescription("Test transfer");
        }

        @Test
        @DisplayName("should debit sender and credit receiver by exact amount")
        void transfer_success_updatesBothBalances() {
            // Arrange
            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));
            when(userRepository.findByEmail("receiver@test.com"))
                    .thenReturn(Optional.of(receiver));
            when(walletRepository.findByUserAndCurrency(receiver, Currency.KES))
                    .thenReturn(Optional.of(receiverKesWallet));
            when(walletRepository.findByIdWithLock(senderKesWallet.getId()))
                    .thenReturn(Optional.of(senderKesWallet));
            when(walletRepository.findByIdWithLock(receiverKesWallet.getId()))
                    .thenReturn(Optional.of(receiverKesWallet));
            when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            TransferResponse response = walletService.transfer(sender, validRequest);

            // Assert
            // Sender: 1000 - 300 = 700
            assertThat(senderKesWallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("700.00"));
            // Receiver: 500 + 300 = 800
            assertThat(receiverKesWallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("800.00"));
            assertThat(response.getSenderNewBalance())
                    .isEqualByComparingTo(new BigDecimal("700.00"));
        }

        @Test
        @DisplayName("should create DEBIT on sender wallet and CREDIT on receiver wallet")
        void transfer_createsTwoLinkedTransactions() {
            // Arrange
            setupSuccessfulTransferMocks();
            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            when(transactionRepository.save(captor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            walletService.transfer(sender, validRequest);

            // Assert — exactly 4 saves: 2 initial + 2 after linking
            List<Transaction> saved = captor.getAllValues();
            assertThat(saved).hasSize(4);

            // Find the initial debit and credit (first two saves)
            Transaction debit  = saved.stream()
                    .filter(t -> t.getType() == TransactionType.DEBIT).findFirst().orElseThrow();
            Transaction credit = saved.stream()
                    .filter(t -> t.getType() == TransactionType.CREDIT).findFirst().orElseThrow();

            assertThat(debit.getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
            assertThat(credit.getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
            assertThat(debit.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(credit.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }

        @Test
        @DisplayName("should throw InvalidTransferException when sender == receiver")
        void transfer_selfTransfer_throwsException() {
            // Arrange — receiver email is the sender's own email
            validRequest.setReceiverEmail("sender@test.com");

            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));
            when(userRepository.findByEmail("sender@test.com"))
                    .thenReturn(Optional.of(sender));
            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));

            // Act & Assert
            assertThatThrownBy(() -> walletService.transfer(sender, validRequest))
                    .isInstanceOf(InvalidTransferException.class)
                    .hasMessageContaining("yourself");

            // No lock acquired, no balance changed, no transactions created
            verify(walletRepository, never()).findByIdWithLock(any());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw InvalidTransferException for cross-currency transfer")
        void transfer_crossCurrency_throwsException() {
            // Arrange — sender sends KES but receiver only matched with USD
            Wallet receiverUsdWallet = new Wallet(receiver, Currency.USD);
            receiverUsdWallet.setId(UUID.randomUUID());

            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));
            when(userRepository.findByEmail("receiver@test.com"))
                    .thenReturn(Optional.of(receiver));
            // Simulate receiver having only a USD wallet for KES query → empty
            when(walletRepository.findByUserAndCurrency(receiver, Currency.KES))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> walletService.transfer(sender, validRequest))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(walletRepository, never()).findByIdWithLock(any());
        }

        @Test
        @DisplayName("should throw InsufficientFundsException when balance < amount")
        void transfer_insufficientFunds_throwsException() {
            // Arrange — try to send 2000 from a 1000 balance wallet
            validRequest.setAmount(new BigDecimal("2000.00"));

            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));
            when(userRepository.findByEmail("receiver@test.com"))
                    .thenReturn(Optional.of(receiver));
            when(walletRepository.findByUserAndCurrency(receiver, Currency.KES))
                    .thenReturn(Optional.of(receiverKesWallet));
            // Return locked wallets with same balance
            when(walletRepository.findByIdWithLock(senderKesWallet.getId()))
                    .thenReturn(Optional.of(senderKesWallet));
            when(walletRepository.findByIdWithLock(receiverKesWallet.getId()))
                    .thenReturn(Optional.of(receiverKesWallet));

            // Act & Assert
            assertThatThrownBy(() -> walletService.transfer(sender, validRequest))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Insufficient");

            // CRITICAL: balances must not have changed
            assertThat(senderKesWallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(receiverKesWallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("500.00"));

            // No transaction records created
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when receiver email not found")
        void transfer_receiverNotFound_throwsException() {
            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));
            when(userRepository.findByEmail("receiver@test.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.transfer(sender, validRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found");

            // No lock, no balance change, no transactions
            verify(walletRepository, never()).findByIdWithLock(any());
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("should not change sender balance when transfer fails")
        void transfer_failure_doesNotChangeSenderBalance() {
            // Arrange — insufficient funds scenario
            validRequest.setAmount(new BigDecimal("5000.00"));
            BigDecimal originalBalance = senderKesWallet.getBalance();

            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));
            when(userRepository.findByEmail("receiver@test.com"))
                    .thenReturn(Optional.of(receiver));
            when(walletRepository.findByUserAndCurrency(receiver, Currency.KES))
                    .thenReturn(Optional.of(receiverKesWallet));
            when(walletRepository.findByIdWithLock(senderKesWallet.getId()))
                    .thenReturn(Optional.of(senderKesWallet));
            when(walletRepository.findByIdWithLock(receiverKesWallet.getId()))
                    .thenReturn(Optional.of(receiverKesWallet));

            // Act
            assertThatThrownBy(() -> walletService.transfer(sender, validRequest))
                    .isInstanceOf(InsufficientFundsException.class);

            // Assert — original balance untouched
            assertThat(senderKesWallet.getBalance())
                    .isEqualByComparingTo(originalBalance);
        }

        @Test
        @DisplayName("should use BigDecimal comparison — not == or .equals for amount check")
        void transfer_usesBigDecimalCompareForFundsCheck() {
            // BigDecimal("1000.00").compareTo(BigDecimal("1000.0")) == 0
            // but BigDecimal("1000.00").equals(BigDecimal("1000.0")) == false
            // This test ensures we use compareTo, not equals

            validRequest.setAmount(new BigDecimal("1000.0")); // exact balance, different scale

            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));  // balance is 1000.00
            when(userRepository.findByEmail("receiver@test.com"))
                    .thenReturn(Optional.of(receiver));
            when(walletRepository.findByUserAndCurrency(receiver, Currency.KES))
                    .thenReturn(Optional.of(receiverKesWallet));
            when(walletRepository.findByIdWithLock(senderKesWallet.getId()))
                    .thenReturn(Optional.of(senderKesWallet));
            when(walletRepository.findByIdWithLock(receiverKesWallet.getId()))
                    .thenReturn(Optional.of(receiverKesWallet));
            when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Should NOT throw — 1000.0 == 1000.00 in value
            assertThatCode(() -> walletService.transfer(sender, validRequest))
                    .doesNotThrowAnyException();
        }

        // Helper — sets up mocks for a successful transfer
        private void setupSuccessfulTransferMocks() {
            when(walletRepository.findByUserAndCurrency(sender, Currency.KES))
                    .thenReturn(Optional.of(senderKesWallet));
            when(userRepository.findByEmail("receiver@test.com"))
                    .thenReturn(Optional.of(receiver));
            when(walletRepository.findByUserAndCurrency(receiver, Currency.KES))
                    .thenReturn(Optional.of(receiverKesWallet));
            when(walletRepository.findByIdWithLock(senderKesWallet.getId()))
                    .thenReturn(Optional.of(senderKesWallet));
            when(walletRepository.findByIdWithLock(receiverKesWallet.getId()))
                    .thenReturn(Optional.of(receiverKesWallet));
            when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }
    }

    
    // CREATE DEFAULT WALLETS
   

    @Nested
    @DisplayName("createDefaultWallets()")
    class CreateDefaultWallets {

        @Test
        @DisplayName("should create one wallet per currency for a new user")
        void createDefaultWallets_createsAllCurrencies() {
            // Arrange — no existing wallets
            when(walletRepository.existsByUserAndCurrency(any(), any())).thenReturn(false);
            when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act
            walletService.createDefaultWallets(sender);

            // Assert — one save per currency (KES + USD = 2)
            verify(walletRepository, times(Currency.values().length)).save(any(Wallet.class));
        }

        @Test
        @DisplayName("should not create duplicate wallets if called multiple times — idempotent")
        void createDefaultWallets_idempotent_skipsExisting() {
            // Arrange — wallets already exist
            when(walletRepository.existsByUserAndCurrency(any(), any())).thenReturn(true);

            // Act — call twice
            walletService.createDefaultWallets(sender);
            walletService.createDefaultWallets(sender);

            // Assert — save never called (wallets already exist)
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("should create wallets with zero balance")
        void createDefaultWallets_startsAtZeroBalance() {
            when(walletRepository.existsByUserAndCurrency(any(), any())).thenReturn(false);

            ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
            when(walletRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            walletService.createDefaultWallets(sender);

            captor.getAllValues().forEach(wallet ->
                assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO));
        }
    }
}