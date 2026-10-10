--liquibase formatted sql

--changeset dedezza1D:005-add-card-pin-lockout
ALTER TABLE card
    ADD COLUMN failed_pin_attempts   INT         NOT NULL DEFAULT 0,
    ADD COLUMN pin_window_started_at DATETIME(6) NULL,
    ADD COLUMN blocked_until         DATETIME(6) NULL;
--rollback ALTER TABLE card DROP COLUMN blocked_until, DROP COLUMN pin_window_started_at, DROP COLUMN failed_pin_attempts;
