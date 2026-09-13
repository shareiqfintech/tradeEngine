-- Phase 2 schema: signal intake, order lifecycle, audit trail, broker
-- position snapshots, and a daily rollup used by the risk engine.

CREATE TABLE trading_signal (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    signal_id         VARCHAR(100)    NOT NULL,
    action            VARCHAR(10)     NOT NULL,
    underlying        VARCHAR(20)     NOT NULL,
    exchange          VARCHAR(10)     NOT NULL,
    timeframe         VARCHAR(10)     NOT NULL,
    price             DECIMAL(18, 4)  NOT NULL,
    signal_timestamp  DATETIME(6)     NOT NULL,
    status            VARCHAR(20)     NOT NULL,
    rejection_reason  VARCHAR(255)    NULL,
    created_at        DATETIME(6)     NOT NULL,
    updated_at        DATETIME(6)     NOT NULL,
    CONSTRAINT uq_trading_signal_signal_id UNIQUE (signal_id)
) ENGINE = InnoDB;

CREATE INDEX idx_trading_signal_underlying_created ON trading_signal (underlying, created_at);

CREATE TABLE orders (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    signal_id           VARCHAR(100)    NOT NULL,
    order_reference_id  VARCHAR(40)     NOT NULL,
    groww_order_id      VARCHAR(100)    NULL,
    underlying          VARCHAR(20)     NOT NULL,
    trading_symbol      VARCHAR(100)    NOT NULL,
    action              VARCHAR(10)     NOT NULL,
    quantity            INT             NOT NULL,
    price               DECIMAL(18, 4)  NULL,
    order_type          VARCHAR(20)     NOT NULL,
    product             VARCHAR(20)     NOT NULL,
    segment             VARCHAR(20)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    filled_quantity     INT             NOT NULL DEFAULT 0,
    average_fill_price  DECIMAL(18, 4)  NULL,
    broker_remark       VARCHAR(500)    NULL,
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    CONSTRAINT uq_orders_order_reference_id UNIQUE (order_reference_id)
) ENGINE = InnoDB;

CREATE INDEX idx_orders_signal_id ON orders (signal_id);
CREATE INDEX idx_orders_groww_order_id ON orders (groww_order_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_underlying_created ON orders (underlying, created_at);

CREATE TABLE audit_event (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    signal_id           VARCHAR(100)    NULL,
    order_reference_id  VARCHAR(40)     NULL,
    event_type          VARCHAR(50)     NOT NULL,
    details             VARCHAR(2000)   NULL,
    created_at          DATETIME(6)     NOT NULL
) ENGINE = InnoDB;

CREATE INDEX idx_audit_event_signal_id ON audit_event (signal_id);
CREATE INDEX idx_audit_event_order_reference_id ON audit_event (order_reference_id);
CREATE INDEX idx_audit_event_created_at ON audit_event (created_at);

CREATE TABLE position_snapshot (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trading_symbol  VARCHAR(100)    NOT NULL,
    exchange        VARCHAR(10)     NOT NULL,
    segment         VARCHAR(20)     NOT NULL,
    quantity        INT             NOT NULL,
    net_quantity    INT             NOT NULL,
    average_price   DECIMAL(18, 4)  NULL,
    product         VARCHAR(20)     NULL,
    captured_at     DATETIME(6)     NOT NULL
) ENGINE = InnoDB;

CREATE INDEX idx_position_snapshot_symbol_captured ON position_snapshot (trading_symbol, captured_at);

CREATE TABLE daily_trading_summary (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trading_date    DATE            NOT NULL,
    orders_count    INT             NOT NULL DEFAULT 0,
    buy_count       INT             NOT NULL DEFAULT 0,
    sell_count      INT             NOT NULL DEFAULT 0,
    rejected_count  INT             NOT NULL DEFAULT 0,
    realized_pnl    DECIMAL(18, 4)  NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    CONSTRAINT uq_daily_trading_summary_date UNIQUE (trading_date)
) ENGINE = InnoDB;
