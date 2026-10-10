package com.miniauthorizer.transaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProcessedTransactionCleanupTest {

    @Mock
    private ProcessedTransactionRepository repository;

    @Test
    void deletesRecordsOlderThanTheRetentionWindow() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

        new ProcessedTransactionCleanup(repository, Duration.ofDays(7), clock).deleteExpired();

        verify(repository).deleteProcessedBefore(Instant.parse("2026-01-08T10:00:00Z"));
    }
}
