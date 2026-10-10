package com.miniauthorizer.transaction;

import com.miniauthorizer.card.CardService;
import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class IdempotentTransactionAuthorizerTest extends MySqlIntegrationTest {

    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Autowired
    private IdempotentTransactionAuthorizer authorizer;

    @Autowired
    private ProcessedTransactionRepository processedTransactions;

    @Autowired
    private CardService cardService;

    @Test
    void debitsOnlyOnceWhenTheTerminalRetries() {
        String card = newCard();
        IdempotencyKey key = newKey();

        authorizer.authorize("terminal", key, new Transaction(card, "1234", TEN));
        authorizer.authorize("terminal", key, new Transaction(card, "1234", TEN));
        authorizer.authorize("terminal", key, new Transaction(card, "1234", TEN));

        assertThat(balance(card)).isEqualByComparingTo("490.00");
    }

    @Test
    void recordsTheAnswerInTheSameDatabaseTransactionAsTheDebit() {
        String card = newCard();
        IdempotencyKey key = newKey();

        authorizer.authorize("terminal", key, new Transaction(card, "1234", TEN));

        ProcessedTransaction record = processedTransactions.findById(new ProcessedTransactionId("terminal", key.value()))
                .orElseThrow();
        assertThat(record.replayFor(new Transaction(card, "1234", TEN))).isEmpty();
        assertThat(record.getProcessedAt()).isCloseTo(Instant.now(), within(java.time.Duration.ofMinutes(1)));
    }

    @Test
    void repeatsARejectionInsteadOfReevaluatingTheRetry() {
        String card = newCard();
        IdempotencyKey key = newKey();

        assertRejected(() -> authorizer.authorize("terminal", key, new Transaction(card, "9999", TEN)),
                RejectionReason.INVALID_PIN);
        assertRejected(() -> authorizer.authorize("terminal", key, new Transaction(card, "1234", TEN)),
                RejectionReason.INVALID_PIN);

        assertThat(balance(card)).isEqualByComparingTo("500.00");
    }

    @Test
    void refusesAKeyReusedForADifferentTransaction() {
        String card = newCard();
        IdempotencyKey key = newKey();
        authorizer.authorize("terminal", key, new Transaction(card, "1234", TEN));

        assertThatThrownBy(() -> authorizer.authorize("terminal", key, new Transaction(card, "1234", new BigDecimal("20.00"))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(balance(card)).isEqualByComparingTo("490.00");
    }

    @Test
    void scopesKeysPerApiClient() {
        String card = newCard();
        IdempotencyKey key = newKey();

        authorizer.authorize("acquirer-a", key, new Transaction(card, "1234", TEN));
        authorizer.authorize("acquirer-b", key, new Transaction(card, "1234", TEN));

        assertThat(balance(card)).isEqualByComparingTo("480.00");
    }

    @Test
    void debitsOnceWhenRetriesArriveWhileTheFirstAttemptIsStillRunning() throws Exception {
        String card = newCard();
        IdempotencyKey key = newKey();

        Map<String, Long> outcomes = fireTogether(20,
                () -> authorizer.authorize("terminal", key, new Transaction(card, "1234", TEN)));

        assertThat(outcomes).containsOnly(Map.entry("APPROVED", 20L));
        assertThat(balance(card)).isEqualByComparingTo("490.00");
    }

    private String newCard() {
        String number = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L, 9_999_999_999_999_999L));
        cardService.issue(number, "1234");
        return number;
    }

    private static IdempotencyKey newKey() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }

    private BigDecimal balance(String card) {
        return cardService.balanceOf(card).orElseThrow();
    }

    private static void assertRejected(Runnable call, RejectionReason reason) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(TransactionRejectedException.class,
                        rejection -> assertThat(rejection.reason()).isEqualTo(reason));
    }

    private static Map<String, Long> fireTogether(int calls, Runnable call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(calls)) {
            List<Future<String>> futures = IntStream.range(0, calls)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        try {
                            call.run();
                            return "APPROVED";
                        } catch (RuntimeException failure) {
                            return failure.getClass().getSimpleName();
                        }
                    }))
                    .toList();
            start.countDown();

            return futures.stream()
                    .map(IdempotentTransactionAuthorizerTest::join)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        }
    }

    private static String join(Future<String> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
