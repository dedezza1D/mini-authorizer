package com.miniauthorizer.card;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PinAttemptsTest {

    private static final String NUMBER = "6549873025634501";
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(30);

    @Mock
    private CardRepository repository;

    private PinAttempts pinAttempts() {
        return new PinAttempts(repository, new PinLockoutProperties(3, WINDOW), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void countsAWrongPinWithinTheWindowAndBlocksForTheSameWindowAtTheLimit() {
        pinAttempts().wrongPin(NUMBER);

        InOrder order = inOrder(repository);
        order.verify(repository).countWrongPin(NUMBER, NOW, NOW.minus(WINDOW));
        order.verify(repository).blockWhenWrongPinsReach(NUMBER, 3, NOW.plus(WINDOW));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void warnsWithAMaskedNumberWhenTheCardGetsBlocked(CapturedOutput output) {
        when(repository.blockWhenWrongPinsReach(anyString(), anyInt(), any())).thenReturn(1);

        pinAttempts().wrongPin(NUMBER);

        assertThat(output.getOut())
                .contains("Card 654987******4501 blocked for PT30M after 3 wrong PINs")
                .doesNotContain(NUMBER);
    }

    @Test
    void staysQuietWhileTheLimitIsNotReached(CapturedOutput output) {
        when(repository.blockWhenWrongPinsReach(anyString(), anyInt(), any())).thenReturn(0);

        pinAttempts().wrongPin(NUMBER);

        assertThat(output.getOut()).doesNotContain("blocked for");
    }

    @Test
    void forgetsEarlierMistakesOnARightPin() {
        Card card = Card.issue(NUMBER, "hash");
        ReflectionTestUtils.setField(card, "failedPinAttempts", 2);

        pinAttempts().rightPin(card);

        verify(repository).forgetWrongPins(NUMBER);
    }

    @Test
    void doesNotWriteOnARightPinWithoutEarlierMistakes() {
        pinAttempts().rightPin(Card.issue(NUMBER, "hash"));

        verifyNoInteractions(repository);
    }
}
