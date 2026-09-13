-- Per-underlying F&O contract-resolution override (expiry/strike/option-type
-- selection, lots, and an optional explicit lot size). No row for an
-- underlying means "use the server-config defaults" - this table never
-- stores a hardcoded lot-size mapping, only the operator's chosen override,
-- which is re-validated against Groww's real instrument master on every use.

CREATE TABLE fno_trade_config (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    underlying        VARCHAR(20)  NOT NULL,
    expiry_selection  VARCHAR(10)  NOT NULL,
    strike_selection  VARCHAR(10)  NOT NULL,
    strike_offset     INT          NOT NULL DEFAULT 0,
    option_type       VARCHAR(10)  NOT NULL,
    lots              INT          NOT NULL,
    lot_size          INT          NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    CONSTRAINT uq_fno_trade_config_underlying UNIQUE (underlying)
) ENGINE = InnoDB;
