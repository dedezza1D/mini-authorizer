package com.miniauthorizer.card;

import java.util.Objects;

public final class CardNumbers {

    // Keeps the issuer prefix (first six digits) and the last four, as PCI DSS allows for display.
    private static final String MIDDLE_DIGITS = "(?<=.{6}).(?=.{4})";

    private CardNumbers() {
    }

    public static String mask(String number) {
        return Objects.requireNonNull(number, "number").replaceAll(MIDDLE_DIGITS, "*");
    }
}
