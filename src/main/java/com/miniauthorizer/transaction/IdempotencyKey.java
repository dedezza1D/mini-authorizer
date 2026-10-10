package com.miniauthorizer.transaction;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Identifier the terminal assigns to a transaction (its NSU, for instance) and repeats when it
 * retries. Keys are scoped per API client, so two acquirers may use the same numbering.
 */
public record IdempotencyKey(String value) {

    public static final String HEADER = "Idempotency-Key";

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public IdempotencyKey {
        value = Optional.ofNullable(value)
                .filter(candidate -> FORMAT.matcher(candidate).matches())
                .orElseThrow(InvalidIdempotencyKeyException::new);
    }
}
