package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;
import com.miniauthorizer.card.PinHasher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class PinMatchesRule implements AuthorizationRule {

    private final PinHasher pinHasher;

    public PinMatchesRule(PinHasher pinHasher) {
        this.pinHasher = pinHasher;
    }

    @Override
    public boolean allows(Card card, Transaction transaction) {
        return card.pinMatches(transaction.pin(), pinHasher);
    }

    @Override
    public RejectionReason rejectionReason() {
        return RejectionReason.INVALID_PIN;
    }
}
