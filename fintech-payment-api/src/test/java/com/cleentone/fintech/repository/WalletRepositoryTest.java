package com.cleentone.fintech.repository;

import com.cleentone.fintech.model.*;
import com.cleentone.fintech.model.enums.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Repository slice tests — uses H2 in-memory DB via @DataJpaTest.
 * No full Spring context. No web layer. No service layer.
 * Tests only the JPA queries against a real database.
 *
 * These catch: wrong JPQL, missing indexes, constraint violations.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("WalletRepository")
class WalletRepositoryTest {

    @Autowired TestEntityManager entityManager;
    @Autowired WalletRepository walletRepository;
    @Autowired UserRepository userRepository;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = new User();
        user1.setEmail("user1@test.com");
        user1.setPasswordHash("$2a$10$hashedpassword");
        user1.setFullName("User One");
        entityManager.persistAndFlush(user1);

        user2 = new User();
        user2.setEmail("user2@test.com");
        user2.setPasswordHash("$2a$10$hashedpassword");
        user2.setFullName("User Two");
        entityManager.persistAndFlush(user2);
    }

    @Test
    @DisplayName("findByUserAndCurrency returns correct wallet")
    void findByUserAndCurrency_returnsCorrectWallet() {
        Wallet wallet = new Wallet(user1, Currency.KES);
        wallet.setBalance(new BigDecimal("1500.00"));
        entityManager.persistAndFlush(wallet);

        Optional<Wallet> found = walletRepository.findByUserAndCurrency(user1, Currency.KES);

        assertThat(found).isPresent();
        assertThat(found.get().getBalance()).isEqualByComparingTo("1500.00");
        assertThat(found.get().getCurrency()).isEqualTo(Currency.KES);
    }

    @Test
    @DisplayName("findByUserAndCurrency returns empty for wrong user")
    void findByUserAndCurrency_wrongUser_returnsEmpty() {
        Wallet wallet = new Wallet(user1, Currency.KES);
        entityManager.persistAndFlush(wallet);

        // user2 has no KES wallet
        Optional<Wallet> found = walletRepository.findByUserAndCurrency(user2, Currency.KES);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByUser returns all wallets for that user only")
    void findByUser_returnsOnlyUserWallets() {
        // user1 has KES and USD
        entityManager.persistAndFlush(new Wallet(user1, Currency.KES));
        entityManager.persistAndFlush(new Wallet(user1, Currency.USD));
        // user2 has only KES
        entityManager.persistAndFlush(new Wallet(user2, Currency.KES));

        List<Wallet> user1Wallets = walletRepository.findByUser(user1);
        List<Wallet> user2Wallets = walletRepository.findByUser(user2);

        assertThat(user1Wallets).hasSize(2);
        assertThat(user2Wallets).hasSize(1);
    }

    @Test
    @DisplayName("existsByUserAndCurrency returns true when wallet exists")
    void existsByUserAndCurrency_exists_returnsTrue() {
        entityManager.persistAndFlush(new Wallet(user1, Currency.KES));

        assertThat(walletRepository.existsByUserAndCurrency(user1, Currency.KES)).isTrue();
    }

    @Test
    @DisplayName("existsByUserAndCurrency returns false when wallet does not exist")
    void existsByUserAndCurrency_notExists_returnsFalse() {
        assertThat(walletRepository.existsByUserAndCurrency(user1, Currency.USD)).isFalse();
    }

    @Test
    @DisplayName("unique constraint prevents duplicate wallets for same user and currency")
    void uniqueConstraint_preventsDuplicateWallet() {
        entityManager.persistAndFlush(new Wallet(user1, Currency.KES));

        Wallet duplicate = new Wallet(user1, Currency.KES);

        assertThatThrownBy(() -> entityManager.persistAndFlush(duplicate))
                .isInstanceOf(Exception.class); // DataIntegrityViolationException or PersistenceException
    }

    @Test
    @DisplayName("same currency allowed for different users")
    void sameCurrency_differentUsers_bothAllowed() {
        entityManager.persistAndFlush(new Wallet(user1, Currency.KES));
        entityManager.persistAndFlush(new Wallet(user2, Currency.KES));

        assertThat(walletRepository.findByUserAndCurrency(user1, Currency.KES)).isPresent();
        assertThat(walletRepository.findByUserAndCurrency(user2, Currency.KES)).isPresent();
    }

    @Test
    @DisplayName("initial balance defaults to zero")
    void newWallet_balanceIsZero() {
        Wallet wallet = new Wallet(user1, Currency.USD);
        entityManager.persistAndFlush(wallet);

        Wallet found = walletRepository.findByUserAndCurrency(user1, Currency.USD).orElseThrow();

        assertThat(found.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}