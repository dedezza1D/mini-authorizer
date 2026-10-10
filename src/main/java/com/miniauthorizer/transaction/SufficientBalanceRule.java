package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class SufficientBalanceRule implements AuthorizationRule {

    @Override
    public boolean allows(Card card, Transaction transaction) {
        return card.canAfford(transaction.amount());
    }

    @Override
    public RejectionReason rejectionReason() {
        return RejectionReason.INSUFFICIENT_BALANCE;
    }
}
