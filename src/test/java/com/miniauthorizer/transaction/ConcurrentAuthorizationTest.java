package com.miniauthorizer.transaction;

import com.miniauthorizer.card.CardService;
import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fires simultaneous transactions at the same card. Each call runs in its own thread, database
 * connection and transaction, which is what two application instances look like to MySQL.
 */
@SpringBootTest
class ConcurrentAuthorizationTest extends MySqlIntegrationTest {

    private static final String APPROVED = "APPROVED";

    @Autowired
    private CardService cardService;

    @Autowired
    private TransactionAuthorizer authorizer;

    @Test
    void approvesOnlyWhatTheBalanceCoversWhenManyTransactionsArriveTogether() throws Exception {
        String number = newCard();

        Map<String, Long> outcomes = fireTogether(60, () -> authorize(number, "10.00"));

        assertThat(outcomes).containsOnly(
                Map.entry(APPROVED, 50L),
                Map.entry(RejectionReason.INSUFFICIENT_BALANCE.name(), 10L));
        assertThat(cardService.balanceOf(number)).hasValueSatisfying(
                balance -> assertThat(balance).isEqualByComparingTo("0.00"));
    }

    @Test
    void twoSimultaneousTransactionsForTheLastTenReaisApproveExactlyOne() throws Exception {
        String number = newCard();
        authorizer.authorize(new Transaction(number, "1234", new BigDecimal("490.00")));

        Map<String, Long> outcomes = fireTogether(2, () -> authorize(number, "10.00"));

        assertThat(outcomes).containsOnly(
                Map.entry(APPROVED, 1L),
                Map.entry(RejectionReason.INSUFFICIENT_BALANCE.name(), 1L));
        assertThat(cardService.balanceOf(number)).hasValueSatisfying(
                balance -> assertThat(balance).isEqualByComparingTo("0.00"));
    }

    private String newCard() {
        String number = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L, 9_999_999_999_999_999L));
        cardService.issue(number, "1234");
        return number;
    }

    private String authorize(String number, String amount) {
        try {
            authorizer.authorize(new Transaction(number, "1234", new BigDecimal(amount)));
            return APPROVED;
        } catch (TransactionRejectedException rejection) {
            return rejection.reason().name();
        } catch (RuntimeException unexpected) {
            return unexpected.getClass().getSimpleName();
        }
    }

    private static Map<String, Long> fireTogether(int calls, Callable<String> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(calls)) {
            List<Future<String>> futures = IntStream.range(0, calls)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return call.call();
                    }))
                    .toList();
            start.countDown();

            return futures.stream()
                    .map(ConcurrentAuthorizationTest::join)
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
