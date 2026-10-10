package com.miniauthorizer.transaction;

import java.util.Objects;

public class TransactionRejectedException extends RuntimeException {

    private final RejectionReason reason;

    public TransactionRejectedException(RejectionReason reason) {
        super(Objects.requireNonNull(reason, "reason").name(), null, false, false);
        this.reason = reason;
    }

    public RejectionReason reason() {
        return reason;
    }
}
