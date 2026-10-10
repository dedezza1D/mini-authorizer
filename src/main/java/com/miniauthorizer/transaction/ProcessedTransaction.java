package com.miniauthorizer.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * The answer given to a transaction sent with an idempotency key, kept so that a retry gets the
 * same answer instead of a second debit. The PIN is not stored.
 */
@Entity
@Table(name = "processed_transaction")
public class ProcessedTransaction {

    @EmbeddedId
    private ProcessedTransactionId id;

    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason")
    private RejectionReason rejectionReason;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    // A null version makes Spring Data persist instead of merge, so a concurrent retry with the
    // same key fails on the primary key instead of overwriting the first answer.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ProcessedTransaction() {
    }

    private ProcessedTransaction(ProcessedTransactionId id, Transaction transaction,
                                 Optional<RejectionReason> rejection, Instant processedAt) {
        this.id = id;
        this.cardNumber = transaction.cardNumber();
        this.amount = transaction.amount();
        this.rejectionReason = rejection.orElse(null);
        this.processedAt = processedAt;
    }

    static ProcessedTransaction record(ProcessedTransactionId id, Transaction transaction,
                                       Optional<RejectionReason> rejection, Instant processedAt) {
        return new ProcessedTransaction(id, transaction, rejection, processedAt);
    }

    /**
     * The answer to give again for a retry of the same transaction: empty when it was approved.
     *
     * @throws IdempotencyKeyReusedException when the key was used for a different card or amount
     */
    Optional<RejectionReason> replayFor(Transaction retry) {
        return Optional.of(this)
                .filter(processed -> processed.isSameRequestAs(retry))
                .map(processed -> Optional.ofNullable(processed.rejectionReason))
                .orElseThrow(IdempotencyKeyReusedException::new);
    }

    private boolean isSameRequestAs(Transaction other) {
        return cardNumber.equals(other.cardNumber()) && amount.compareTo(other.amount()) == 0;
    }

    ProcessedTransactionId getId() {
        return id;
    }

    Instant getProcessedAt() {
        return processedAt;
    }
}
