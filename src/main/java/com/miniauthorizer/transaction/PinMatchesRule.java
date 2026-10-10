package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;
import com.miniauthorizer.card.PinAttempts;
import com.miniauthorizer.card.PinHasher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class PinMatchesRule implements AuthorizationRule {

    private final PinHasher pinHasher;
    private final PinAttempts pinAttempts;

    public PinMatchesRule(PinHasher pinHasher, PinAttempts pinAttempts) {
        this.pinHasher = pinHasher;
        this.pinAttempts = pinAttempts;
    }

    @Override
    public boolean allows(Card card, Transaction transaction) {
        return card.pinMatches(transaction.pin(), pinHasher);
    }

    @Override
    public RejectionReason rejectionReason() {
        return RejectionReason.INVALID_PIN;
    }

    @Override
    public void onAllowed(Card card, Transaction transaction) {
        pinAttempts.rightPin(card);
    }

    @Override
    public void onRejected(Card card, Transaction transaction) {
        pinAttempts.wrongPin(card.getNumber());
    }
}
