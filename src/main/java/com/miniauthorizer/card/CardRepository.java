package com.miniauthorizer.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

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

    /**
     * Counts a wrong PIN. A failure after the window expired starts a new window with one attempt,
     * so old mistakes never add up to a block.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Card c
               set c.failedPinAttempts = case
                       when c.pinWindowStartedAt is null or c.pinWindowStartedAt < :windowStart then 1
                       else c.failedPinAttempts + 1 end,
                   c.pinWindowStartedAt = case
                       when c.pinWindowStartedAt is null or c.pinWindowStartedAt < :windowStart then :now
                       else c.pinWindowStartedAt end
             where c.number = :number
            """)
    int countWrongPin(@Param("number") String number, @Param("now") Instant now,
                      @Param("windowStart") Instant windowStart);

    /** Blocks the card once the wrong PINs in the window reach the limit; the count starts over after. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Card c
               set c.blockedUntil = :until, c.failedPinAttempts = 0, c.pinWindowStartedAt = null
             where c.number = :number and c.failedPinAttempts >= :maxAttempts
            """)
    int blockWhenWrongPinsReach(@Param("number") String number, @Param("maxAttempts") int maxAttempts,
                                @Param("until") Instant until);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Card c
               set c.failedPinAttempts = 0, c.pinWindowStartedAt = null
             where c.number = :number and c.failedPinAttempts > 0
            """)
    int forgetWrongPins(@Param("number") String number);
}
