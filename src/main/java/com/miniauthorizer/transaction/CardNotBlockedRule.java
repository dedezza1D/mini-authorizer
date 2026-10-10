package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Runs before the PIN rule: a blocked card is refused without looking at the PIN, so guesses
 * made during the block neither count nor reveal whether they were right.
 */
@Component
@Order(1)
public class CardNotBlockedRule implements AuthorizationRule {

    private final Clock clock;

    public CardNotBlockedRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean allows(Card card, Transaction transaction) {
        return !card.isBlockedAt(clock.instant());
    }

    @Override
    public RejectionReason rejectionReason() {
        return RejectionReason.CARD_BLOCKED;
    }
}
