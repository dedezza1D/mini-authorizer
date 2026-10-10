package com.miniauthorizer.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessedTransactionTest {

    private static final ProcessedTransactionId ID = new ProcessedTransactionId("terminal", "nsu-1");
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Transaction TRANSACTION = new Transaction("6549873025634501", "1234", new BigDecimal("10.00"));

    @Test
    void replaysAnApprovalAsNoRejection() {
        ProcessedTransaction approved = ProcessedTransaction.record(ID, TRANSACTION, Optional.empty(), NOW);

        assertThat(approved.replayFor(TRANSACTION)).isEmpty();
        assertThat(approved.getId()).isEqualTo(ID);
        assertThat(approved.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void replaysARejectionWithTheSameReason() {
        ProcessedTransaction rejected = ProcessedTransaction.record(ID, TRANSACTION,
                Optional.of(RejectionReason.INSUFFICIENT_BALANCE), NOW);

        assertThat(rejected.replayFor(TRANSACTION)).contains(RejectionReason.INSUFFICIENT_BALANCE);
    }

    @Test
    void treatsTheSameAmountWithAnotherScaleAsTheSameRequest() {
        ProcessedTransaction approved = ProcessedTransaction.record(ID, TRANSACTION, Optional.empty(), NOW);

        assertThat(approved.replayFor(new Transaction("6549873025634501", "1234", new BigDecimal("10"))))
                .isEmpty();
    }

    @Test
    void refusesAKeyReusedForAnotherAmount() {
        ProcessedTransaction approved = ProcessedTransaction.record(ID, TRANSACTION, Optional.empty(), NOW);

        assertThatThrownBy(() -> approved.replayFor(new Transaction("6549873025634501", "1234", new BigDecimal("10.01"))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void refusesAKeyReusedForAnotherCard() {
        ProcessedTransaction approved = ProcessedTransaction.record(ID, TRANSACTION, Optional.empty(), NOW);

        assertThatThrownBy(() -> approved.replayFor(new Transaction("6549873025634502", "1234", new BigDecimal("10.00"))))
                .isInstanceOf(IdempotencyKeyReusedException.class)
                .hasMessage("Idempotency-Key was already used for a different transaction");
    }
}
