package com.miniauthorizer.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;

/**
 * Authorizes a transaction at most once per idempotency key, so a terminal can safely retry
 * after a timeout.
 * <p>
 * The answer is recorded in the same database transaction as the debit, and the key is the
 * primary key of that record. Two consequences:
 * <ul>
 *   <li>a retry after the first attempt committed finds the record and gets the same answer,
 *       including a rejection, without running the rules or debiting again;</li>
 *   <li>a retry that races the first attempt fails on the primary key when inserting its record,
 *       which rolls back its own debit, and then answers from the record the winner committed.</li>
 * </ul>
 */
@Service
public class IdempotentTransactionAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentTransactionAuthorizer.class);

    private final TransactionAuthorizer authorizer;
    private final ProcessedTransactionRepository repository;
    private final TransactionTemplate databaseTransaction;
    private final Clock clock;

    public IdempotentTransactionAuthorizer(TransactionAuthorizer authorizer,
                                           ProcessedTransactionRepository repository,
                                           PlatformTransactionManager transactionManager,
                                           Clock clock) {
        this.authorizer = authorizer;
        this.repository = repository;
        this.databaseTransaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * @throws TransactionRejectedException  when the transaction is (or was, for a retry) rejected
     * @throws IdempotencyKeyReusedException when the key belongs to a different transaction
     */
    public void authorize(String clientId, IdempotencyKey key, Transaction transaction) {
        ProcessedTransactionId id = ProcessedTransactionId.of(clientId, key);

        repository.findById(id)
                .map(previous -> replay(previous, transaction))
                .orElseGet(() -> processOnce(id, transaction))
                .ifPresent(reason -> {
                    throw new TransactionRejectedException(reason);
                });
    }

    private Optional<RejectionReason> processOnce(ProcessedTransactionId id, Transaction transaction) {
        try {
            return databaseTransaction.execute(status -> {
                Optional<RejectionReason> rejection = attempt(transaction);
                repository.saveAndFlush(ProcessedTransaction.record(id, transaction, rejection, clock.instant()));
                return rejection;
            });
        } catch (DataIntegrityViolationException concurrentRetry) {
            return repository.findById(id)
                    .map(winner -> replay(winner, transaction))
                    .orElseThrow(() -> concurrentRetry);
        }
    }

    private Optional<RejectionReason> attempt(Transaction transaction) {
        try {
            authorizer.authorize(transaction);
            return Optional.empty();
        } catch (TransactionRejectedException rejection) {
            return Optional.of(rejection.reason());
        }
    }

    private static Optional<RejectionReason> replay(ProcessedTransaction previous, Transaction retry) {
        Optional<RejectionReason> answer = previous.replayFor(retry);
        log.info("Retry answered from the first attempt: {}", retry);
        return answer;
    }
}
