package com.miniauthorizer.card;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Wrong-PIN accounting for cards. A 4-digit PIN has only 10,000 values, so without a limit a
 * client of the API could simply try them all; issuers block the card after a few wrong PINs.
 * <p>
 * Here {@code maxAttempts} wrong PINs within {@code window} block the card for {@code window};
 * afterwards the count starts from zero. Every change is a conditional UPDATE, so the limit
 * holds across instances and concurrent attempts are all counted. The caller's database
 * transaction must commit even when the transaction is rejected, or the count is lost.
 */
@Component
@EnableConfigurationProperties(PinLockoutProperties.class)
public class PinAttempts {

    private static final Logger log = LoggerFactory.getLogger(PinAttempts.class);

    private final CardRepository repository;
    private final PinLockoutProperties lockout;
    private final Clock clock;

    public PinAttempts(CardRepository repository, PinLockoutProperties lockout, Clock clock) {
        this.repository = repository;
        this.lockout = lockout;
        this.clock = clock;
    }

    public void wrongPin(String cardNumber) {
        Instant now = clock.instant();
        repository.countWrongPin(cardNumber, now, now.minus(lockout.window()));

        Optional.of(repository.blockWhenWrongPinsReach(cardNumber, lockout.maxAttempts(), now.plus(lockout.window())))
                .filter(blockedRows -> blockedRows > 0)
                .ifPresent(blocked -> log.warn("Card {} blocked for {} after {} wrong PINs",
                        CardNumbers.mask(cardNumber), lockout.window(), lockout.maxAttempts()));
    }

    /** A right PIN proves the holder: earlier mistakes are forgotten, without a write when there were none. */
    public void rightPin(Card card) {
        Optional.of(card)
                .filter(Card::hasFailedPinAttempts)
                .ifPresent(withMistakes -> repository.forgetWrongPins(withMistakes.getNumber()));
    }
}
