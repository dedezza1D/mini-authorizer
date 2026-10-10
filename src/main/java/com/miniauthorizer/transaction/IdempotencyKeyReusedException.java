package com.miniauthorizer.transaction;

/**
 * The key was already used for a different card or amount: it is not a retry, and processing
 * it would either hide a new transaction or replay an unrelated answer.
 */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super(IdempotencyKey.HEADER + " was already used for a different transaction", null, false, false);
    }
}
