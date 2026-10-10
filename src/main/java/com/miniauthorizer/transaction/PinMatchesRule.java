package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class PinMatchesRule implements AuthorizationRule {

    private final PasswordEncoder passwordEncoder;

    public PinMatchesRule(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean allows(Card card, Transaction transaction) {
        return card.pinMatches(transaction.pin(), passwordEncoder);
    }

    @Override
    public RejectionReason rejectionReason() {
        return RejectionReason.INVALID_PIN;
    }
}
