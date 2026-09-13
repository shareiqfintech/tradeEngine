-- User accounts and per-user Groww API credentials for the new
-- authentication + Groww Settings feature. Credentials are stored
-- encrypted (see CredentialEncryptionService) - never in plaintext, and
-- never returned in full over the API.

CREATE TABLE app_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(120)  NOT NULL,
    email          VARCHAR(190)  NOT NULL,
    password_hash  VARCHAR(100)  NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE groww_configuration (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                BIGINT        NOT NULL,
    api_key_encrypted      VARCHAR(2000) NULL,
    totp_secret_encrypted  VARCHAR(2000) NULL,
    enabled                BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    CONSTRAINT uq_groww_configuration_user_id UNIQUE (user_id),
    CONSTRAINT fk_groww_configuration_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB;
