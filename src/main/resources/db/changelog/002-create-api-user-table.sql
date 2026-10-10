--liquibase formatted sql

--changeset dedezza1D:002-create-api-user-table
CREATE TABLE api_user (
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL,
    CONSTRAINT pk_api_user PRIMARY KEY (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
--rollback DROP TABLE api_user;
