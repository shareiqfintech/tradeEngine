import type {
  AuditEventType,
  OrderStatus,
  SignalStatus,
  TradingAction,
  TradingMode,
} from './domain'

/* ============================================================================
 * REAL, CURRENTLY-IMPLEMENTED backend response shapes.
 * Source: trading-webhook-engine/src/main/java/com/example/trading/dto/*.java
 * and .../controller/TradingAdminController.java
 * ==========================================================================*/

/** GET /api/trading/status - com.example.trading.dto.TradingStatusResponse */
export interface TradingStatusResponse {
  mode: TradingMode
  /** True only when a brand-new entry may be opened right now (09:25-15:10 IST, no kill switch / pause). */
  tradingEnabled: boolean
  growwAuthenticated: boolean
  killSwitch: boolean
  paused: boolean
  ordersToday: number
  openPositions: number
  /** PRE_MARKET | TRADING_ACTIVE | SESSION_CLOSING | SAFETY_EXIT_COMPLETED | MARKET_CLOSED | KILL_SWITCH | PAUSED | AUTH_REQUIRED */
  sessionState: string
  /** "09:25" - strategy session start (IST). */
  sessionStart: string
  /** "15:10" - SAFETY EXIT / new-entry cutoff (IST). NOT the market close. */
  tradingCutoff: string
  /** "15:30" - official NSE close (IST). */
  officialMarketClose: string
  /** "Asia/Kolkata" */
  timezone: string
}

/** POST /api/trading/{pause,resume,kill-switch/enable,kill-switch/disable} - com.example.trading.dto.WebhookResponse */
export interface SimpleActionResponse {
  status: string
  signalId: string | null
}

/** Uniform error body from com.example.trading.exception.GlobalExceptionHandler (ErrorResponse.java) */
export interface ApiErrorBody {
  timestamp?: string
  status: number
  error?: string
  message: string
  path?: string
  fieldErrors?: Record<string, string>
}

/* ============================================================================
 * EXPECTED / NOT-YET-IMPLEMENTED backend response shapes.
 *
 * These mirror the backend's actual JPA entities (so that once the
 * corresponding read endpoints are added, the shape should already match),
 * but as of this build NONE of the following endpoints exist in
 * TradingAdminController or anywhere else in the backend:
 *
 *   GET /api/trading/signals            (paginated list of trading_signal rows)
 *   GET /api/trading/signals/{signalId} (single signal + execution timeline)
 *   GET /api/trading/orders             (paginated list of orders rows)
 *   GET /api/trading/orders/{id}        (single order detail)
 *   GET /api/trading/positions          (list of broker positions)
 *   GET /api/trading/risk               (risk config + live usage counters)
 *   GET /api/trading/audit              (paginated audit_event rows)
 *
 * The API layer (src/api/*) calls these exact paths and the UI renders
 * honest loading/error/empty states when the backend responds 404/501 -
 * it never substitutes fabricated data. See frontend/README.md "API
 * contract assumptions" for the full list.
 * ==========================================================================*/

/** Expected shape of one row from GET /api/trading/signals - mirrors TradingSignalEntity. */
export interface TradingSignal {
  id: number
  signalId: string
  action: TradingAction
  underlying: string
  exchange: string
  timeframe: string
  price: number
  signalTimestamp: string
  status: SignalStatus
  rejectionReason: string | null
  createdAt: string
  updatedAt: string
}

/**
 * Expected shape of GET /api/trading/signals/{signalId} - the entity plus an
 * execution timeline. The backend does not currently persist a distinct
 * per-stage timeline table; AuditEventEntity rows scoped to this signalId
 * are the closest real substitute, so this type composes the two rather
 * than inventing a timeline field that doesn't exist server-side.
 */
export interface TradingSignalDetail extends TradingSignal {
  auditTrail: AuditEvent[]
  order: TradingOrder | null
}

/** Expected shape of one row from GET /api/trading/orders - mirrors OrderEntity. */
export interface TradingOrder {
  id: number
  signalId: string
  orderReferenceId: string
  growwOrderId: string | null
  underlying: string
  tradingSymbol: string
  action: TradingAction
  quantity: number
  price: number | null
  orderType: string
  product: string
  segment: string
  status: OrderStatus
  filledQuantity: number
  averageFillPrice: number | null
  brokerRemark: string | null
  createdAt: string
  updatedAt: string
}

export interface TradingOrderDetail extends TradingOrder {
  auditTrail: AuditEvent[]
  signal: TradingSignal | null
}

/**
 * Expected shape of one row from GET /api/trading/positions - mirrors the
 * backend's internal `Position` DTO (com.example.trading.dto.Position)
 * exactly for the fields that DO exist there. `optionType`/`strike`/`expiry`
 * are NOT backend fields - the UI derives them purely for display by
 * parsing `tradingSymbol` (see src/lib/optionSymbol.ts), never for trading
 * logic. `ltp`, `unrealizedPnl`, and `dayPnl` are NOT computed by the
 * backend today (Position.java has no such fields) - they are optional here
 * and rendered as "-" rather than fabricated when absent.
 */
export interface TradingPosition {
  tradingSymbol: string
  exchange: string
  segment: string
  quantity: number
  averagePrice: number | null
  product: string
  netQuantity: number
  ltp?: number | null
  unrealizedPnl?: number | null
  dayPnl?: number | null
}

/**
 * Expected shape of GET /api/trading/risk. The limits mirror
 * TradingProperties.Risk (already real, server-side config); the *current
 * usage* counters (ordersToday, openPositions, dailyLossSoFar) are computed
 * today inside RiskManagementService per-signal but never returned by any
 * endpoint as a standalone snapshot - this is the documented gap.
 */
export interface RiskStatusResponse {
  maxOrdersPerDay: number
  ordersToday: number
  maxOpenPositions: number
  openPositions: number
  maxQuantity: number
  quantityUsedToday: number | null
  maxDailyLoss: number
  dailyLossSoFar: number
  maxOrdersPerSignal: number
  allowShortSelling: boolean
}

/** Expected shape of one row from GET /api/trading/audit - mirrors AuditEventEntity. */
export interface AuditEvent {
  id: number
  signalId: string | null
  orderReferenceId: string | null
  eventType: AuditEventType
  details: string | null
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

/**
 * Expected shape of NOT-YET-IMPLEMENTED GET /api/trading/pnl/summary and
 * GET /api/trading/pnl/intraday.
 *
 * `daily_trading_summary.realized_pnl` is a REAL column
 * (DailyTradingSummaryEntity.realizedPnl) but nothing in the backend
 * currently writes a non-zero value to it - TradingEngineService.updateDailySummary()
 * only increments orders/buy/sell/rejected counts. Unrealized P&L and
 * win/loss counts have no backend source at all today. The Dashboard
 * therefore only renders these once this endpoint exists; until then it
 * shows an honest "not available" state, never a fabricated number.
 */
export interface PnlSummaryResponse {
  todayPnl: number
  realizedPnl: number
  unrealizedPnl: number
  winCount: number | null
  lossCount: number | null
}

export interface PnlPoint {
  time: string
  pnl: number
}

export interface SignalFilters {
  action?: TradingAction
  status?: SignalStatus
  underlying?: string
  search?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface OrderFilters {
  action?: TradingAction
  status?: OrderStatus
  tradingSymbol?: string
  mode?: TradingMode
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface AuditFilters {
  eventType?: AuditEventType
  signalId?: string
  orderReferenceId?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

/**
 * Expected shape of GET /actuator/health (real endpoint - Spring Boot
 * Actuator, already exposed via `management.endpoints.web.exposure.include:
 * health,info` in application.yml). `components` is only populated when the
 * backend sets `management.endpoint.health.show-details: always` (or
 * `when-authorized`) - NOT currently set, so today this will typically only
 * contain `status`. See frontend/README.md.
 */
export interface ActuatorHealthResponse {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'
  components?: Record<string, ActuatorHealthComponent>
}

export interface ActuatorHealthComponent {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'
  details?: Record<string, unknown>
}

/**
 * Expected shape of a NOT-YET-IMPLEMENTED GET /api/health/summary endpoint,
 * covering the operational detail the Health page's spec asks for (last
 * Groww auth, last webhook, last order, uptime) that has no real backend
 * source today - no endpoint anywhere returns these timestamps.
 */
export interface HealthSummaryResponse {
  lastGrowwAuthAt: string | null
  lastWebhookReceivedAt: string | null
  lastOrderAt: string | null
  applicationUptimeSeconds: number | null
}
