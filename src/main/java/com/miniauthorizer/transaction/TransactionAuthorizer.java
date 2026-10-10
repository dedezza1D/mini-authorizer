package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;
import com.miniauthorizer.card.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(TransactionAuthorizer.class);

    private final CardRepository cardRepository;
    private final List<AuthorizationRule> rules;

    public TransactionAuthorizer(CardRepository cardRepository, List<AuthorizationRule> rules) {
        this.cardRepository = cardRepository;
        this.rules = List.copyOf(rules);
    }

    /**
     * Debits the card when every rule allows the transaction.
     *
     * @throws TransactionRejectedException with the first rule that refused it
     */
    @Transactional
    public void authorize(Transaction transaction) {
        Card card = cardRepository.findById(transaction.cardNumber())
                .orElseThrow(() -> new TransactionRejectedException(RejectionReason.CARD_NOT_FOUND));

        rules.forEach(rule -> rule.check(card, transaction));
        card.debit(transaction.amount());

        log.info("Transaction authorized: {}", transaction);
    }
}
