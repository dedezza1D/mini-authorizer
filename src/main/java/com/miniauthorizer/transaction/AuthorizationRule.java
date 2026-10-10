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

    /** Reaction to a transaction this rule allowed. Does nothing unless the rule needs it. */
    default void onAllowed(Card card, Transaction transaction) {
    }

    /** Reaction to a transaction this rule refused, before the refusal is reported. */
    default void onRejected(Card card, Transaction transaction) {
    }

    default void check(Card card, Transaction transaction) {
        Optional.of(card)
                .filter(candidate -> allows(candidate, transaction))
                .ifPresentOrElse(allowed -> onAllowed(allowed, transaction), () -> {
                    onRejected(card, transaction);
                    throw new TransactionRejectedException(rejectionReason());
                });
    }
}
