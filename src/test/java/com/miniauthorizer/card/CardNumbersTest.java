package com.miniauthorizer.card;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CardNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "6549873025634501, 654987******4501",
            "4111111111111, 411111***1111",
            "6011000990139424123, 601100*********4123"
    })
    void keepsOnlyTheIssuerPrefixAndTheLastFourDigits(String number, String masked) {
        assertThat(CardNumbers.mask(number)).isEqualTo(masked);
    }
}
