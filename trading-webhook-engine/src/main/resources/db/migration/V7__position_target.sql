-- One row per long option position that has an automatic profit target.
--
-- Created when a BUY entry order is placed; moves to MONITORING once Groww
-- confirms a real filled price (target_price = entry_price + target_points,
-- snapshotted so a later config change never moves an open position's
-- target). The single TargetMonitorScheduler polls the exact option LTP and
-- submits exactly one SELL (exit_order_reference_id, deterministic) when
-- LTP >= target_price. All state lives here so monitoring survives restarts.

CREATE TABLE position_target (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                   BIGINT        NOT NULL,
    signal_id                 VARCHAR(100)  NOT NULL,
    entry_order_reference_id  VARCHAR(40)   NOT NULL,
    groww_entry_order_id      VARCHAR(100)  NULL,
    trading_symbol            VARCHAR(100)  NOT NULL,
    exchange                  VARCHAR(20)   NOT NULL,
    segment                   VARCHAR(20)   NOT NULL,
    underlying                VARCHAR(20)   NOT NULL,
    side                      VARCHAR(10)   NOT NULL,
    quantity                  INT           NOT NULL,
    entry_price               DECIMAL(18, 4) NULL,
    target_points             DECIMAL(18, 4) NOT NULL,
    target_enabled            BOOLEAN       NOT NULL DEFAULT TRUE,
    target_price              DECIMAL(18, 4) NULL,
    target_status             VARCHAR(20)   NOT NULL,
    last_ltp                  DECIMAL(18, 4) NULL,
    last_ltp_at               DATETIME(6)   NULL,
    ltp_at_trigger            DECIMAL(18, 4) NULL,
    exit_order_reference_id   VARCHAR(40)   NULL,
    groww_exit_order_id       VARCHAR(100)  NULL,
    exited_quantity           INT           NULL,
    closed_at                 DATETIME(6)   NULL,
    version                   BIGINT        NOT NULL DEFAULT 0,
    created_at                DATETIME(6)   NOT NULL,
    updated_at                DATETIME(6)   NOT NULL,
    CONSTRAINT uq_position_target_entry_order UNIQUE (entry_order_reference_id)
);

CREATE INDEX idx_position_target_status ON position_target (target_status);
CREATE INDEX idx_position_target_user_status ON position_target (user_id, target_status);
