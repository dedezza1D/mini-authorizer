--liquibase formatted sql

--changeset dedezza1D:001-create-card-table
CREATE TABLE card (
    card_number VARCHAR(19)    NOT NULL,
    pin_hash    VARCHAR(100)   NOT NULL,
    balance     DECIMAL(15, 2) NOT NULL,
    version     BIGINT         NOT NULL,
    CONSTRAINT pk_card PRIMARY KEY (card_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE card;
