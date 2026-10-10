package com.miniauthorizer.transaction;

import com.miniauthorizer.card.CardService;
import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The whole lockout through the real rules, transactions and database.
 */
@SpringBootTest
class PinLockoutTest extends MySqlIntegrationTest {

    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Autowired
    private TransactionAuthorizer authorizer;

    @Autowired
    private IdempotentTransactionAuthorizer idempotentAuthorizer;

    @Autowired
    private CardService cardService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void blocksTheCardOnTheThirdWrongPinEvenForTheRightOne() {
        String card = newCard();

        assertRejected(card, "0000", RejectionReason.INVALID_PIN);
        assertRejected(card, "1111", RejectionReason.INVALID_PIN);
        assertRejected(card, "2222", RejectionReason.INVALID_PIN);

        assertRejected(card, "1234", RejectionReason.CARD_BLOCKED);
        assertThat(cardService.balanceOf(card)).hasValueSatisfying(
                balance -> assertThat(balance).isEqualByComparingTo("500.00"));
    }

    @Test
    void doesNotCountGuessesMadeWhileBlocked() {
        String card = blockedCard();

        assertRejected(card, "3333", RejectionReason.CARD_BLOCKED);
        unblock(card);

        assertThatCode(() -> authorizer.authorize(new Transaction(card, "1234", TEN))).doesNotThrowAnyException();
    }

    @Test
    void acceptsTheRightPinAgainOnceTheBlockExpires() {
        String card = blockedCard();
        unblock(card);

        authorizer.authorize(new Transaction(card, "1234", TEN));

        assertThat(cardService.balanceOf(card)).hasValueSatisfying(
                balance -> assertThat(balance).isEqualByComparingTo("490.00"));
    }

    @Test
    void forgetsMistakesOnceTheRightPinIsEntered() {
        String card = newCard();
        assertRejected(card, "0000", RejectionReason.INVALID_PIN);
        assertRejected(card, "1111", RejectionReason.INVALID_PIN);

        authorizer.authorize(new Transaction(card, "1234", TEN));

        assertRejected(card, "2222", RejectionReason.INVALID_PIN);
        assertRejected(card, "3333", RejectionReason.INVALID_PIN);
        assertThatCode(() -> authorizer.authorize(new Transaction(card, "1234", TEN)))
                .as("two mistakes after a right PIN must not block the card")
                .doesNotThrowAnyException();
    }

    @Test
    void countsWrongPinsSentWithAnIdempotencyKeyToo() {
        String card = newCard();

        for (int attempt = 0; attempt < 3; attempt++) {
            IdempotencyKey key = new IdempotencyKey("nsu-" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
            assertThatThrownBy(() -> idempotentAuthorizer.authorize("terminal", key, new Transaction(card, "0000", TEN)))
                    .isInstanceOf(TransactionRejectedException.class);
        }

        assertRejected(card, "1234", RejectionReason.CARD_BLOCKED);
    }

    private String newCard() {
        String number = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L, 9_999_999_999_999_999L));
        cardService.issue(number, "1234");
        return number;
    }

    private String blockedCard() {
        String card = newCard();
        assertRejected(card, "0000", RejectionReason.INVALID_PIN);
        assertRejected(card, "1111", RejectionReason.INVALID_PIN);
        assertRejected(card, "2222", RejectionReason.INVALID_PIN);
        return card;
    }

    private void unblock(String card) {
        jdbc.update("update card set blocked_until = ? where card_number = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), card);
    }

    private void assertRejected(String card, String pin, RejectionReason reason) {
        assertThatThrownBy(() -> authorizer.authorize(new Transaction(card, pin, TEN)))
                .isInstanceOfSatisfying(TransactionRejectedException.class,
                        rejection -> assertThat(rejection.reason()).isEqualTo(reason));
    }
}
