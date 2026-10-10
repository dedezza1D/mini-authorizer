package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;

import java.util.Optional;

/**
 * One authorization rule (Strategy). Rules are Spring beans applied in {@code @Order}
 * sequence, so a new rule is a new class and the authorizer itself does not change.
 */
public interface AuthorizationRule {

    boolean allows(Card card, Transaction transaction);

    RejectionReason rejectionReason();

    default void check(Card card, Transaction transaction) {
        Optional.of(card)
                .filter(candidate -> allows(candidate, transaction))
                .orElseThrow(() -> new TransactionRejectedException(rejectionReason()));
    }
}
