package com.miniauthorizer.transaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedTransactionIdTest {

    @Test
    void scopesTheKeyToTheApiClient() {
        assertThat(ProcessedTransactionId.of("acquirer-a", new IdempotencyKey("nsu-1")))
                .isEqualTo(new ProcessedTransactionId("acquirer-a", "nsu-1"))
                .isNotEqualTo(ProcessedTransactionId.of("acquirer-b", new IdempotencyKey("nsu-1")));
    }
}
