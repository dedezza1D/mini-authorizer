package com.miniauthorizer.card;

import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CardRepositoryTest extends MySqlIntegrationTest {

    private static final String NUMBER = "6549873025634501";

    @Autowired
    private CardRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsCardWithInitialBalanceKeepingTwoDecimalPlaces() {
        repository.saveAndFlush(Card.issue(NUMBER, "hash"));
        entityManager.clear();

        Card stored = repository.findById(NUMBER).orElseThrow();

        assertThat(stored.getBalance()).isEqualByComparingTo("500.00");
        assertThat(stored.getBalance().scale()).isEqualTo(2);
        assertThat(stored.getPinHash()).isEqualTo("hash");
    }

    @Test
    void doesNotOverwriteAnExistingCard() {
        repository.saveAndFlush(Card.issue(NUMBER, "original-hash"));
        entityManager.clear();

        assertThatThrownBy(() -> repository.saveAndFlush(Card.issue(NUMBER, "other-hash")))
                .isInstanceOf(DataIntegrityViolationException.class);

        entityManager.clear();
        assertThat(repository.findById(NUMBER)).get()
                .extracting(Card::getPinHash).isEqualTo("original-hash");
    }

    @Test
    void debitsWhenTheBalanceCoversTheAmount() {
        repository.saveAndFlush(Card.issue(NUMBER, "hash"));

        int updated = repository.debitIfAffordable(NUMBER, new BigDecimal("4.85"));

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findById(NUMBER)).get()
                .satisfies(card -> assertThat(card.getBalance()).isEqualByComparingTo("495.15"));
    }

    @Test
    void debitsTheWholeBalanceButNotOneCentMore() {
        repository.saveAndFlush(Card.issue(NUMBER, "hash"));

        assertThat(repository.debitIfAffordable(NUMBER, new BigDecimal("500.01"))).isZero();
        assertThat(repository.debitIfAffordable(NUMBER, new BigDecimal("500.00"))).isEqualTo(1);
        assertThat(repository.debitIfAffordable(NUMBER, new BigDecimal("0.01"))).isZero();

        assertThat(repository.findById(NUMBER)).get()
                .satisfies(card -> assertThat(card.getBalance()).isEqualByComparingTo("0.00"));
    }

    @Test
    void debitsNothingForUnknownCard() {
        assertThat(repository.debitIfAffordable("0000000000000000", BigDecimal.ONE)).isZero();
    }
}
