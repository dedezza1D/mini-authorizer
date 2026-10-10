--liquibase formatted sql

--changeset dedezza1D:003-add-api-user-lockout
ALTER TABLE api_user
    ADD COLUMN failed_attempts INT         NOT NULL DEFAULT 0,
    ADD COLUMN locked_until    DATETIME(6) NULL;
--rollback ALTER TABLE api_user DROP COLUMN locked_until, DROP COLUMN failed_attempts;
