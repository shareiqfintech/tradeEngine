-- One TradingView webhook is a single common trading signal, executed
-- independently for EVERY application user with a connected Groww account
-- (see TradingEngineService/GrowwUserResolver) - trading_signal stays one
-- row per alert; signal_execution records what happened for each user,
-- unique per (signal_id, user_id) so a signal can never be executed twice
-- for the same user.
--
-- orders.user_id makes explicit which application user's own Groww account
-- an order was placed on behalf of - previously implicit, since only one
-- trading user could ever exist at a time.

CREATE TABLE signal_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    signal_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_signal_execution_signal_user UNIQUE (signal_id, user_id)
);

CREATE INDEX idx_signal_execution_signal_id ON signal_execution (signal_id);

ALTER TABLE orders ADD COLUMN user_id BIGINT NULL;
