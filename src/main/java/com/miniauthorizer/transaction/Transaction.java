package com.miniauthorizer.transaction;

import com.miniauthorizer.card.CardNumbers;

import java.math.BigDecimal;
import java.util.Objects;

public record Transaction(String cardNumber, String pin, BigDecimal amount) {

    public Transaction {
        Objects.requireNonNull(cardNumber, "cardNumber");
        Objects.requireNonNull(pin, "pin");
        Objects.requireNonNull(amount, "amount");
    }

    // Keeps the PIN out of logs and exception messages.
    @Override
    public String toString() {
        return "Transaction[cardNumber=%s, amount=%s]".formatted(CardNumbers.mask(cardNumber), amount);
    }
}
