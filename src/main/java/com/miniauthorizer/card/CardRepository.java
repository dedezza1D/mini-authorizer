package com.miniauthorizer.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface CardRepository extends JpaRepository<Card, String> {

    /**
     * Debits the card in a single conditional statement. The row lock taken by the UPDATE
     * serializes concurrent debits on the same card, in this or any other application instance,
     * and the balance condition is evaluated against the latest committed value.
     *
     * @return 1 when the card was debited, 0 when it does not exist or cannot afford the amount
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Card c
               set c.balance = c.balance - :amount, c.version = c.version + 1
             where c.number = :number and c.balance >= :amount
            """)
    int debitIfAffordable(@Param("number") String number, @Param("amount") BigDecimal amount);
}
