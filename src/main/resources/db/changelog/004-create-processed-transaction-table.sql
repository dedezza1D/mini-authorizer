--liquibase formatted sql

--changeset dedezza1D:004-create-processed-transaction-table
CREATE TABLE processed_transaction (
    client_id        VARCHAR(50)    NOT NULL,
    idempotency_key  VARCHAR(64)    NOT NULL,
    card_number      VARCHAR(19)    NOT NULL,
    amount           DECIMAL(15, 2) NOT NULL,
    rejection_reason VARCHAR(30)    NULL,
    processed_at     DATETIME(6)    NOT NULL,
    version          BIGINT         NOT NULL,
    CONSTRAINT pk_processed_transaction PRIMARY KEY (client_id, idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX ix_processed_transaction_processed_at ON processed_transaction (processed_at);
--rollback DROP TABLE processed_transaction;
