package com.miniauthorizer.transaction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"1", "000123456", "terminal-42_nsu-000123",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void acceptsUpToSixtyFourLettersDigitsDashesAndUnderscores(String value) {
        assertThat(new IdempotencyKey(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "nsu 123", "nsu/123", "nsu;drop",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsAnythingElse(String value) {
        assertThatThrownBy(() -> new IdempotencyKey(value))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key must have 1 to 64 letters, digits, '-' or '_'");
    }
}
