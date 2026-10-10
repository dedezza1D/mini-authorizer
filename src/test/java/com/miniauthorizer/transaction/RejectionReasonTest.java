package com.miniauthorizer.transaction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RejectionReasonTest {

    @ParameterizedTest
    @CsvSource({
            "CARD_NOT_FOUND, CARTAO_INEXISTENTE",
            "INVALID_PIN, SENHA_INVALIDA",
            "INSUFFICIENT_BALANCE, SALDO_INSUFICIENTE"
    })
    void carriesTheCodeDefinedByTheApiContract(RejectionReason reason, String code) {
        assertThat(reason.code()).isEqualTo(code);
    }
}
