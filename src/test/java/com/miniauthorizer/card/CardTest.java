package com.miniauthorizer.card;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CardTest {

    private static final String NUMBER = "6549873025634501";

    @Test
    void newCardStartsWithFiveHundred() {
        Card card = Card.issue(NUMBER, "hash");

        assertThat(card.getNumber()).isEqualTo(NUMBER);
        assertThat(card.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void requiresNumberAndPinHash() {
        assertThatNullPointerException().isThrownBy(() -> Card.issue(null, "hash")).withMessage("number");
        assertThatNullPointerException().isThrownBy(() -> Card.issue(NUMBER, null)).withMessage("pinHash");
    }

    @Test
    void canAffordUpToTheWholeBalance() {
        Card card = Card.issue(NUMBER, "hash");

        assertThat(card.canAfford(new BigDecimal("499.99"))).isTrue();
        assertThat(card.canAfford(new BigDecimal("500.00"))).isTrue();
        assertThat(card.canAfford(new BigDecimal("500.01"))).isFalse();
    }

    @Test
    void debitSubtractsTheAmountFromTheBalance() {
        Card card = Card.issue(NUMBER, "hash");

        card.debit(new BigDecimal("4.85"));

        assertThat(card.getBalance()).isEqualByComparingTo("495.15");
    }

    @Test
    void debitCanEmptyTheBalanceButNeverMakeItNegative() {
        Card card = Card.issue(NUMBER, "hash");
        card.debit(new BigDecimal("500.00"));

        assertThat(card.getBalance()).isEqualByComparingTo("0.00");
        assertThatIllegalStateException().isThrownBy(() -> card.debit(new BigDecimal("0.01")));
        assertThat(card.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void checksPinAgainstTheStoredHash() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        Card card = Card.issue(NUMBER, encoder.encode("1234"));

        assertThat(card.pinMatches("1234", encoder)).isTrue();
        assertThat(card.pinMatches("4321", encoder)).isFalse();
    }
}
