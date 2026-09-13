-- F&O trade configuration becomes PER-USER (was global per-underlying), and
-- gains an automatic profit-target setting.
--
--  * user_id           : the owning application user (multi-user support)
--  * target_points      : "TARGET PRICE = actual filled entry price + target_points"
--                         NULL = use trading.exit.target.default-points
--  * target_enabled     : per-underlying toggle for the auto-exit feature
--
-- The unique key moves from (underlying) to (user_id, underlying) so User A
-- and User B keep independent lots / strike / target settings.

ALTER TABLE fno_trade_config ADD COLUMN user_id BIGINT NULL;
ALTER TABLE fno_trade_config ADD COLUMN target_points DECIMAL(18, 4) NULL;
ALTER TABLE fno_trade_config ADD COLUMN target_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE fno_trade_config DROP CONSTRAINT uq_fno_trade_config_underlying;
ALTER TABLE fno_trade_config ADD CONSTRAINT uq_fno_trade_config_user_underlying UNIQUE (user_id, underlying);
