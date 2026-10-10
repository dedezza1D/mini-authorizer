package com.miniauthorizer.card;

import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wrong-PIN statements against real MySQL: the window and block logic lives in SQL.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CardPinLockoutRepositoryTest extends MySqlIntegrationTest {

    private static final String NUMBER = "6549873025634501";
    private static final Duration WINDOW = Duration.ofMinutes(30);
    private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private CardRepository repository;

    @BeforeEach
    void card() {
        repository.saveAndFlush(Card.issue(NUMBER, "hash"));
    }

    @Test
    void blocksAfterThreeWrongPinsWithinTheWindowAndStartsCountingAgain() {
        wrongPinAt(T0);
        wrongPinAt(T0.plus(Duration.ofMinutes(10)));
        assertThat(repository.blockWhenWrongPinsReach(NUMBER, 3, T0.plus(Duration.ofMinutes(50)))).isZero();

        wrongPinAt(T0.plus(Duration.ofMinutes(20)));
        assertThat(repository.blockWhenWrongPinsReach(NUMBER, 3, T0.plus(Duration.ofMinutes(50)))).isEqualTo(1);

        Card blocked = stored();
        assertThat(blocked.isBlockedAt(T0.plus(Duration.ofMinutes(49)))).isTrue();
        assertThat(blocked.isBlockedAt(T0.plus(Duration.ofMinutes(50)))).isFalse();
        assertThat(blocked.hasFailedPinAttempts()).as("the count starts over after a block").isFalse();
    }

    @Test
    void forgetsWrongPinsOlderThanTheWindow() {
        wrongPinAt(T0);
        wrongPinAt(T0.plus(Duration.ofMinutes(10)));

        wrongPinAt(T0.plus(Duration.ofMinutes(31)));

        assertThat(ReflectionTestUtils.getField(stored(), "failedPinAttempts"))
                .as("a wrong PIN after the window opens a new window with one attempt")
                .isEqualTo(1);
        assertThat(repository.blockWhenWrongPinsReach(NUMBER, 3, T0.plus(Duration.ofHours(1)))).isZero();
    }

    @Test
    void forgetsWrongPinsAfterARightOne() {
        wrongPinAt(T0);
        wrongPinAt(T0.plus(Duration.ofMinutes(1)));

        assertThat(repository.forgetWrongPins(NUMBER)).isEqualTo(1);
        assertThat(stored().hasFailedPinAttempts()).isFalse();
        assertThat(repository.forgetWrongPins(NUMBER)).as("nothing left to forget, nothing written").isZero();
    }

    @Test
    void ignoresUnknownCards() {
        assertThat(repository.countWrongPin("0000000000000000", T0, T0.minus(WINDOW))).isZero();
        assertThat(repository.blockWhenWrongPinsReach("0000000000000000", 0, T0)).isZero();
    }

    private void wrongPinAt(Instant now) {
        repository.countWrongPin(NUMBER, now, now.minus(WINDOW));
    }

    private Card stored() {
        return repository.findById(NUMBER).orElseThrow();
    }
}
