package com.miniauthorizer.transaction;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super(IdempotencyKey.HEADER + " must have 1 to 64 letters, digits, '-' or '_'", null, false, false);
    }
}
