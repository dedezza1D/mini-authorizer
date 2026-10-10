package com.miniauthorizer.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "card")
public class Card {

    public static final BigDecimal INITIAL_BALANCE = new BigDecimal("500.00");

    @Id
    @Column(name = "card_number")
    private String number;

    @Column(name = "pin_hash", nullable = false)
    private String pinHash;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    // A null version marks the entity as new: with an application-assigned id, Spring Data
    // then calls persist instead of merge, so a duplicate number fails on the primary key
    // instead of silently overwriting the existing card.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Card() {
    }

    private Card(String number, String pinHash, BigDecimal balance) {
        this.number = Objects.requireNonNull(number, "number");
        this.pinHash = Objects.requireNonNull(pinHash, "pinHash");
        this.balance = balance;
    }

    public static Card issue(String number, String pinHash) {
        return new Card(number, pinHash, INITIAL_BALANCE);
    }

    public String getNumber() {
        return number;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    String getPinHash() {
        return pinHash;
    }
}
