package com.miniauthorizer.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TransactionTest {

    @Test
    void neverExposesThePinOrTheFullCardNumber() {
        Transaction transaction = new Transaction("6549873025634501", "9876", new BigDecimal("10.00"));

        assertThat(transaction.toString())
                .isEqualTo("Transaction[cardNumber=654987******4501, amount=10.00]")
                .doesNotContain("9876");
    }

    @Test
    void requiresAllFields() {
        assertThatNullPointerException().isThrownBy(() -> new Transaction(null, "1234", BigDecimal.ONE));
        assertThatNullPointerException().isThrownBy(() -> new Transaction("6549873025634501", null, BigDecimal.ONE));
        assertThatNullPointerException().isThrownBy(() -> new Transaction("6549873025634501", "1234", null));
    }
}
