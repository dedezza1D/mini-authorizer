package com.miniauthorizer.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

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

    // The PIN lockout fields are written only through conditional UPDATEs in CardRepository, so
    // wrong PINs sent to several instances at once are all counted.
    @Column(name = "failed_pin_attempts", nullable = false)
    private int failedPinAttempts;

    @Column(name = "pin_window_started_at")
    private Instant pinWindowStartedAt;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

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

    public boolean pinMatches(String pin, PinHasher pinHasher) {
        return pinHasher.matches(number, pin, pinHash);
    }

    public boolean canAfford(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public boolean isBlockedAt(Instant instant) {
        return Optional.ofNullable(blockedUntil).filter(instant::isBefore).isPresent();
    }

    public boolean hasFailedPinAttempts() {
        return failedPinAttempts > 0;
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
