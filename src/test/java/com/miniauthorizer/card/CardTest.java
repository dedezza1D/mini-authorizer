package com.miniauthorizer.card;

import com.miniauthorizer.support.TestPins;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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
    void checksPinAgainstTheStoredHash() {
        Card card = Card.issue(NUMBER, TestPins.HASHER.hash(NUMBER, "1234"));

        assertThat(card.pinMatches("1234", TestPins.HASHER)).isTrue();
        assertThat(card.pinMatches("4321", TestPins.HASHER)).isFalse();
    }

    @Test
    void newCardIsNeitherBlockedNorCarryingMistakes() {
        Card card = Card.issue(NUMBER, "hash");

        assertThat(card.isBlockedAt(Instant.now())).isFalse();
        assertThat(card.hasFailedPinAttempts()).isFalse();
    }

    @Test
    void isBlockedOnlyUntilTheBlockExpires() {
        Instant until = Instant.parse("2026-01-15T10:30:00Z");
        Card card = Card.issue(NUMBER, "hash");
        ReflectionTestUtils.setField(card, "blockedUntil", until);

        assertThat(card.isBlockedAt(until.minusMillis(1))).isTrue();
        assertThat(card.isBlockedAt(until)).isFalse();
    }

    @Test
    void remembersWrongPins() {
        Card card = Card.issue(NUMBER, "hash");
        ReflectionTestUtils.setField(card, "failedPinAttempts", 1);

        assertThat(card.hasFailedPinAttempts()).isTrue();
    }
}
