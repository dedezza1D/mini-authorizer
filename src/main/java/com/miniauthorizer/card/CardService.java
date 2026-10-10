package com.miniauthorizer.card;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class CardService {

    private final CardRepository repository;
    private final PinHasher pinHasher;

    public CardService(CardRepository repository, PinHasher pinHasher) {
        this.repository = repository;
        this.pinHasher = pinHasher;
    }

    public CardIssuance issue(String number, String pin) {
        return repository.findById(number)
                .map(existing -> CardIssuance.ALREADY_EXISTS)
                .orElseGet(() -> persist(number, pin));
    }

    public Optional<BigDecimal> balanceOf(String number) {
        return repository.findById(number).map(Card::getBalance);
    }

    // Two requests for the same number can both pass the lookup above; the primary key
    // decides which one wins and the other is reported as an existing card.
    private CardIssuance persist(String number, String pin) {
        try {
            repository.saveAndFlush(Card.issue(number, pinHasher.hash(number, pin)));
            return CardIssuance.CREATED;
        } catch (DataIntegrityViolationException concurrentInsert) {
            return CardIssuance.ALREADY_EXISTS;
        }
    }
}
