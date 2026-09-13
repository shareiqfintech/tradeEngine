/**
 * Enum-shaped unions mirroring the backend's Java enums EXACTLY
 * (com.example.trading.enums.*). `erasableSyntaxOnly` in tsconfig disallows
 * real TS `enum` declarations, so these are string-literal unions + a
 * matching `as const` array (useful for iterating in filters/selects).
 *
 * Source of truth: trading-webhook-engine/src/main/java/com/example/trading/enums/
 */

// com.example.trading.enums.TradingAction
export const TRADING_ACTIONS = ['BUY', 'SELL'] as const
export type TradingAction = (typeof TRADING_ACTIONS)[number]

// com.example.trading.enums.TradingMode
export const TRADING_MODES = ['PAPER', 'LIVE'] as const
export type TradingMode = (typeof TRADING_MODES)[number]

// com.example.trading.enums.SignalStatus
export const SIGNAL_STATUSES = [
  'RECEIVED',
  'VALIDATED',
  'PROCESSING',
  'EXECUTED',
  'REJECTED',
  'DUPLICATE',
  'FAILED',
] as const
export type SignalStatus = (typeof SIGNAL_STATUSES)[number]

// com.example.trading.enums.OrderStatus
export const ORDER_STATUSES = [
  'PENDING',
  'OPEN',
  'COMPLETE',
  'REJECTED',
  'CANCELLED',
  'FAILED',
] as const
export type OrderStatus = (typeof ORDER_STATUSES)[number]

// com.example.trading.enums.OptionType
export const OPTION_TYPES = ['CE', 'PE'] as const
export type OptionType = (typeof OPTION_TYPES)[number]

// com.example.trading.enums.GrowwAuthState
// NOTE: not currently returned by any endpoint (GET /api/trading/status only
// exposes a `growwAuthenticated` boolean). Kept here for the day the backend
// exposes the full state machine; UI code must not assume it is present.
export const GROWW_AUTH_STATES = [
  'AUTHENTICATED',
  'AUTH_REQUIRED',
  'AUTH_FAILED',
  'TOKEN_EXPIRED',
] as const
export type GrowwAuthState = (typeof GROWW_AUTH_STATES)[number]

// com.example.trading.enums.AuditEventType
export const AUDIT_EVENT_TYPES = [
  'SIGNAL_RECEIVED',
  'SIGNAL_SAVED',
  'SIGNAL_VALIDATED',
  'DUPLICATE_SIGNAL',
  'SIGNAL_REJECTED',
  'MARKET_CLOSED',
  'TRADING_DISABLED',
  'AUTH_SUCCESS',
  'AUTH_FAILED',
  'TOKEN_EXPIRED',
  'POSITION_CHECK',
  'NO_LONG_POSITION',
  'POSITION_CLOSE_VERIFIED',
  'POSITION_CLOSE_INCOMPLETE',
  'CONTRACT_RESOLVED',
  'CONTRACT_NOT_FOUND',
  'RISK_APPROVED',
  'RISK_REJECTED',
  'PAPER_ORDER',
  'LIVE_ORDER_SUBMITTED',
  'ORDER_REJECTED',
  'ORDER_STATUS_UPDATED',
  'ORDER_COMPLETED',
  'RECONCILIATION_MISMATCH',
  'KILL_SWITCH_ENABLED',
  'KILL_SWITCH_DISABLED',
  'TRADING_PAUSED',
  'TRADING_RESUMED',
  // Trading-session lifecycle (09:25 start / 15:10 safety exit / 15:30 official close)
  'SESSION_STARTED',
  'NEW_ENTRY_REJECTED_SESSION_NOT_STARTED',
  'NEW_ENTRY_REJECTED_TRADING_CUTOFF',
  'SESSION_CLOSING_STARTED',
  'AUTO_CLOSE_POSITION_STARTED',
  'AUTO_CLOSE_ORDER_SUBMITTED',
  'AUTO_CLOSE_ORDER_FILLED',
  'AUTO_CLOSE_ORDER_FAILED',
  'SAFETY_EXIT_COMPLETED',
  'OFFICIAL_MARKET_CLOSED',
  // Automatic profit-target exit (TARGET-ONLY)
  'TARGET_MONITOR_STARTED',
  'TARGET_MONITOR_DEFERRED',
  'TARGET_MONITOR_RESUMED',
  'TARGET_LTP_UNAVAILABLE',
  'TARGET_HIT',
  'TARGET_EXIT_ORDER_SUBMITTED',
  'TARGET_EXIT_ORDER_FILLED',
  'TARGET_EXIT_ORDER_FAILED',
  'TARGET_POSITION_CLOSED',
] as const
export type AuditEventType = (typeof AUDIT_EVENT_TYPES)[number]

/** Client-side-only derived status - not a backend enum. Used to color risk usage bars. */
export type RiskLevel = 'SAFE' | 'WARNING' | 'CRITICAL' | 'BLOCKED'

/** Client-side-only derived status - not a backend enum. Used for health rows without a real indicator. */
export type ComponentHealth = 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN'
