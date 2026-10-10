package com.miniauthorizer.transaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentTransactionAuthorizerUnitTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final IdempotencyKey KEY = new IdempotencyKey("nsu-1");
    private static final ProcessedTransactionId ID = new ProcessedTransactionId("terminal", "nsu-1");
    private static final Transaction TRANSACTION = new Transaction("6549873025634501", "1234", new BigDecimal("10.00"));

    @Mock
    private TransactionAuthorizer authorizer;

    @Mock
    private ProcessedTransactionRepository repository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private IdempotentTransactionAuthorizer idempotent() {
        return new IdempotentTransactionAuthorizer(authorizer, repository, transactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void authorizesANewKeyAndRecordsTheApprovalUnderTheCallersScope() {
        when(repository.findById(ID)).thenReturn(Optional.empty());
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        assertThatCode(() -> idempotent().authorize("terminal", KEY, TRANSACTION)).doesNotThrowAnyException();

        ProcessedTransaction recorded = recorded();
        verify(authorizer).authorize(TRANSACTION);
        assertThat(recorded.getId()).isEqualTo(ID);
        assertThat(recorded.getProcessedAt()).isEqualTo(NOW);
        assertThat(recorded.replayFor(TRANSACTION)).isEmpty();
        verify(transactionManager).commit(any());
    }

    @Test
    void recordsARejectionAndThenReportsIt() {
        when(repository.findById(ID)).thenReturn(Optional.empty());
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doThrow(new TransactionRejectedException(RejectionReason.INVALID_PIN)).when(authorizer).authorize(TRANSACTION);

        assertRejected(RejectionReason.INVALID_PIN);

        assertThat(recorded().replayFor(TRANSACTION)).contains(RejectionReason.INVALID_PIN);
        verify(transactionManager).commit(any());
    }

    @Test
    void answersAnApprovedRetryWithoutAuthorizingAgain() {
        when(repository.findById(ID)).thenReturn(Optional.of(processed(Optional.empty())));

        assertThatCode(() -> idempotent().authorize("terminal", KEY, TRANSACTION)).doesNotThrowAnyException();

        verifyNoInteractions(authorizer, transactionManager);
    }

    @Test
    void answersARejectedRetryWithTheSameReasonWithoutAuthorizingAgain() {
        when(repository.findById(ID)).thenReturn(Optional.of(processed(Optional.of(RejectionReason.INSUFFICIENT_BALANCE))));

        assertRejected(RejectionReason.INSUFFICIENT_BALANCE);

        verifyNoInteractions(authorizer, transactionManager);
    }

    @Test
    void rollsBackItsOwnDebitAndAnswersWithTheWinnerWhenARetryRacesTheFirstAttempt() {
        when(repository.findById(ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(processed(Optional.of(RejectionReason.INSUFFICIENT_BALANCE))));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertRejected(RejectionReason.INSUFFICIENT_BALANCE);

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void propagatesTheConstraintViolationWhenNoWinnerCanBeFound() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("Data too long");
        when(repository.findById(ID)).thenReturn(Optional.empty());
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(repository.saveAndFlush(any())).thenThrow(violation);

        assertThatThrownBy(() -> idempotent().authorize("terminal", KEY, TRANSACTION)).isSameAs(violation);
    }

    @Test
    void refusesAKeyAlreadyUsedForAnotherTransaction() {
        when(repository.findById(ID)).thenReturn(Optional.of(processed(Optional.empty())));

        assertThatThrownBy(() -> idempotent().authorize("terminal", KEY,
                new Transaction("6549873025634501", "1234", new BigDecimal("99.00"))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    private ProcessedTransaction recorded() {
        ArgumentCaptor<ProcessedTransaction> recorded = ArgumentCaptor.forClass(ProcessedTransaction.class);
        verify(repository).saveAndFlush(recorded.capture());
        return recorded.getValue();
    }

    private static ProcessedTransaction processed(Optional<RejectionReason> rejection) {
        return ProcessedTransaction.record(ID, TRANSACTION, rejection, NOW);
    }

    private void assertRejected(RejectionReason reason) {
        assertThatThrownBy(() -> idempotent().authorize("terminal", KEY, TRANSACTION))
                .isInstanceOfSatisfying(TransactionRejectedException.class,
                        rejection -> assertThat(rejection.reason()).isEqualTo(reason));
    }
}
