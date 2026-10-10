package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;
import com.miniauthorizer.card.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TransactionAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(TransactionAuthorizer.class);
    private static final int DEBITED = 1;

    private final CardRepository cardRepository;
    private final List<AuthorizationRule> rules;

    public TransactionAuthorizer(CardRepository cardRepository, List<AuthorizationRule> rules) {
        this.cardRepository = cardRepository;
        this.rules = List.copyOf(rules);
    }

    /**
     * Debits the card when every rule allows the transaction.
     * <p>
     * The rules run against the card as read at the start, which is enough to reject wrong PINs
     * and amounts that obviously do not fit. The debit itself is a conditional UPDATE: if another
     * transaction spent the balance in the meantime, no row changes and the transaction is
     * rejected for insufficient balance instead of overdrawing the card.
     *
     * @throws TransactionRejectedException with the first rule that refused it
     */
    // A rejection writes nothing, and must not doom a surrounding transaction that records it.
    @Transactional(noRollbackFor = TransactionRejectedException.class)
    public void authorize(Transaction transaction) {
        Card card = cardRepository.findById(transaction.cardNumber())
                .orElseThrow(() -> new TransactionRejectedException(RejectionReason.CARD_NOT_FOUND));

        rules.forEach(rule -> rule.check(card, transaction));

        Optional.of(cardRepository.debitIfAffordable(transaction.cardNumber(), transaction.amount()))
                .filter(updatedRows -> updatedRows == DEBITED)
                .orElseThrow(() -> new TransactionRejectedException(RejectionReason.INSUFFICIENT_BALANCE));

        log.info("Transaction authorized: {}", transaction);
    }
}
