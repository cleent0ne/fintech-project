package com.cleentone.fintech.repository;

import com.cleentone.fintech.model.*;
import com.cleentone.fintech.model.enums.Currency;
import com.cleentone.fintech.model.enums.TransactionType;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TransactionRepository")
class TransactionRepositoryTest {

    @Autowired TestEntityManager entityManager;
    @Autowired TransactionRepository transactionRepository;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("test@test.com");
        user.setPasswordHash("$2a$10$hash");
        user.setFullName("Test User");
        entityManager.persistAndFlush(user);

        wallet = new Wallet(user, Currency.KES);
        wallet.setBalance(new BigDecimal("5000.00"));
        entityManager.persistAndFlush(wallet);
    }

    private Transaction makeTransaction(TransactionType type, String amount, String ref) {
        Transaction tx = Transaction.create(
                wallet, type,
                new BigDecimal(amount),
                new BigDecimal(amount),
                ref, "Test", null);
        return entityManager.persistAndFlush(tx);
    }

    @Test
    @DisplayName("findByWallet returns only transactions for that wallet")
    void findByWallet_returnsCorrectTransactions() {
        makeTransaction(TransactionType.CREDIT, "500.00", UUID.randomUUID().toString());
        makeTransaction(TransactionType.DEBIT,  "200.00", UUID.randomUUID().toString());

        Page<Transaction> page = transactionRepository.findByWallet(
                wallet, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByWallet respects pagination")
    void findByWallet_pagination_works() {
        // Create 5 transactions
        for (int i = 0; i < 5; i++) {
            makeTransaction(TransactionType.CREDIT, "100.00", UUID.randomUUID().toString());
        }

        Page<Transaction> page = transactionRepository.findByWallet(
                wallet, PageRequest.of(0, 3)); // page size 3

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByWallet sorted descending returns newest first")
    void findByWallet_sortedDescending_newestFirst() throws InterruptedException {
        Transaction first  = makeTransaction(TransactionType.CREDIT, "100.00", UUID.randomUUID().toString());
        Thread.sleep(10);
        Transaction second = makeTransaction(TransactionType.CREDIT, "200.00", UUID.randomUUID().toString());

        Page<Transaction> page = transactionRepository.findByWallet(
                wallet,
                PageRequest.of(0, 10, Sort.by("createdAt").descending()));

        // Most recent (second) should be first in result
        assertThat(page.getContent().get(0).getId()).isEqualTo(second.getId());
        assertThat(page.getContent().get(1).getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("existsByReference returns true for existing reference")
    void existsByReference_exists_returnsTrue() {
        String ref = "unique-ref-123";
        makeTransaction(TransactionType.CREDIT, "100.00", ref);

        assertThat(transactionRepository.existsByReference(ref)).isTrue();
    }

    @Test
    @DisplayName("existsByReference returns false for non-existent reference")
    void existsByReference_notExists_returnsFalse() {
        assertThat(transactionRepository.existsByReference("nonexistent-ref")).isFalse();
    }

    @Test
    @DisplayName("unique constraint on reference prevents duplicate transactions")
    void uniqueReference_preventsIdempotencyBug() {
        String sameRef = "duplicate-ref";
        makeTransaction(TransactionType.CREDIT, "100.00", sameRef);

        Transaction duplicate = Transaction.create(
                wallet, TransactionType.CREDIT,
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                sameRef, "Duplicate", null);

        // DB must reject the duplicate reference
        assertThatThrownBy(() -> entityManager.persistAndFlush(duplicate))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("findByReference returns correct transaction")
    void findByReference_returnsTransaction() {
        String ref = "find-by-ref-test";
        Transaction tx = makeTransaction(TransactionType.DEBIT, "300.00", ref);

        var found = transactionRepository.findByReference(ref);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(tx.getId());
        assertThat(found.get().getAmount()).isEqualByComparingTo("300.00");
    }
}