package com.miniauthorizer.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Deletes idempotency records older than the retention window. Terminals only retry for a few
 * minutes, so keeping a few days is plenty and stops the table from growing forever. Running it
 * on several instances at once is harmless: the delete is idempotent.
 */
@Component
public class ProcessedTransactionCleanup {

    private static final Logger log = LoggerFactory.getLogger(ProcessedTransactionCleanup.class);

    private final ProcessedTransactionRepository repository;
    private final Duration retention;
    private final Clock clock;

    public ProcessedTransactionCleanup(ProcessedTransactionRepository repository,
                                       @Value("${app.idempotency.retention:P7D}") Duration retention,
                                       Clock clock) {
        this.repository = repository;
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-interval:PT1H}")
    public void deleteExpired() {
        int deleted = repository.deleteProcessedBefore(clock.instant().minus(retention));
        log.debug("Deleted {} idempotency records older than {}", deleted, retention);
    }
}
