package com.miniauthorizer.transaction;

import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessedTransactionRepositoryTest extends MySqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Transaction TRANSACTION = new Transaction("6549873025634501", "1234", new BigDecimal("10.00"));

    @Autowired
    private ProcessedTransactionRepository repository;

    @Test
    void neverOverwritesTheFirstAnswerForAKey() {
        ProcessedTransactionId id = new ProcessedTransactionId("terminal", "nsu-1");
        repository.saveAndFlush(ProcessedTransaction.record(id, TRANSACTION, Optional.empty(), NOW));

        assertThatThrownBy(() -> repository.saveAndFlush(ProcessedTransaction.record(id, TRANSACTION,
                Optional.of(RejectionReason.INVALID_PIN), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletesOnlyRecordsProcessedBeforeTheCutoff() {
        // Other test classes commit records processed "now"; a cutoff far in the past leaves them alone.
        ProcessedTransactionId old = new ProcessedTransactionId("terminal", "old");
        ProcessedTransactionId recent = new ProcessedTransactionId("terminal", "recent");
        repository.saveAndFlush(ProcessedTransaction.record(old, TRANSACTION, Optional.empty(), NOW.minusSeconds(1)));
        repository.saveAndFlush(ProcessedTransaction.record(recent, TRANSACTION,
                Optional.of(RejectionReason.INSUFFICIENT_BALANCE), NOW));

        assertThat(repository.deleteProcessedBefore(NOW)).isEqualTo(1);
        assertThat(repository.findById(old)).isEmpty();
        assertThat(repository.findById(recent)).isPresent();
    }
}
