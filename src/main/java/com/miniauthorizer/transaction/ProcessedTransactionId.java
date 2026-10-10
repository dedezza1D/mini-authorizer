package com.miniauthorizer.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ProcessedTransactionId(
        @Column(name = "client_id") String clientId,
        @Column(name = "idempotency_key") String idempotencyKey) {

    static ProcessedTransactionId of(String clientId, IdempotencyKey key) {
        return new ProcessedTransactionId(clientId, key.value());
    }
}
