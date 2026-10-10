package com.miniauthorizer.card;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CardTest {

    @Test
    void newCardStartsWithFiveHundred() {
        Card card = Card.issue("6549873025634501", "hash");

        assertThat(card.getNumber()).isEqualTo("6549873025634501");
        assertThat(card.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void requiresNumberAndPinHash() {
        assertThatNullPointerException().isThrownBy(() -> Card.issue(null, "hash")).withMessage("number");
        assertThatNullPointerException().isThrownBy(() -> Card.issue("6549873025634501", null)).withMessage("pinHash");
    }
}
