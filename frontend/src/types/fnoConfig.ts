import type { OptionType } from './domain'

/** AUTO resolves CE for BUY signals server-side (see OptionContractResolver) - not a frontend decision. */
export const FNO_OPTION_TYPE_SELECTIONS = ['AUTO', 'CE', 'PE'] as const
export type FnoOptionTypeSelection = (typeof FNO_OPTION_TYPE_SELECTIONS)[number]

export const FNO_STRIKE_SELECTIONS = ['ATM', 'ITM', 'OTM'] as const
export type FnoStrikeSelection = (typeof FNO_STRIKE_SELECTIONS)[number]

export const FNO_EXPIRY_SELECTIONS = ['NEAREST', 'NEXT'] as const
export type FnoExpirySelection = (typeof FNO_EXPIRY_SELECTIONS)[number]

export type LotSizeSource = 'USER' | 'DEFAULT'

/**
 * Mirrors com.example.trading.dto.OptionContract exactly - the resolved,
 * order-ready contract as the backend sees it. Every field here (including
 * lotSize) comes from Groww's real instrument master, never a frontend
 * constant.
 */
export interface ResolvedOptionContract {
  tradingSymbol: string
  underlying: string
  expiry: string
  strike: number
  optionType: OptionType
  lotSize: number
  exchange: string
  segment: string
  buyAllowed: boolean
  sellAllowed: boolean
}

/** PUT /api/trading/fno/config/{underlying} request body - mirrors FnoTradeConfigRequest. lotSize omitted/undefined = no explicit override (use the resolved contract's own current lot size). */
export interface FnoTradeConfigRequest {
  optionType: FnoOptionTypeSelection
  strikeSelection: FnoStrikeSelection
  strikeOffset: number
  expirySelection: FnoExpirySelection
  lots: number
  lotSize?: number | null
  /**
   * Automatic profit-target size in POINTS (not a price). null/undefined =
   * use the server default. Validated server-side against min/max; the
   * backend recomputes the target PRICE from the real Groww fill.
   */
  targetPoints?: number | null
  /** null/undefined = leave the current per-underlying toggle unchanged. */
  targetEnabled?: boolean | null
}

/**
 * GET/PUT /api/trading/fno/config/{underlying} response - mirrors
 * FnoTradeConfigResponse exactly. `lotSize` is always the EFFECTIVE value
 * the backend actually used; `lotSizeSource` says whether that came from
 * an explicit user selection or the contract's own default. `quantity` is
 * computed server-side (lots * lotSize) - the frontend must never treat
 * its own lots*lotSize arithmetic as authoritative, only display it.
 */
export interface FnoTradeConfigResponse {
  underlying: string
  optionType: FnoOptionTypeSelection
  strikeSelection: FnoStrikeSelection
  strikeOffset: number
  expirySelection: FnoExpirySelection
  lots: number
  lotSize: number
  lotSizeSource: LotSizeSource
  validLotSizes: number[]
  quantity: number
  resolvedContract: ResolvedOptionContract
  /**
   * EFFECTIVE Target Points (saved value, or the server default). There is
   * deliberately no `targetPrice` here - it is "Calculated after entry"
   * from the actual Groww fill, never previewed from the signal/spot price.
   */
  targetPoints: number
  targetEnabled: boolean
  targetPointsMin: number
  targetPointsMax: number
}

/** com.example.trading.enums.TargetStatus */
export const TARGET_STATUSES = [
  'PENDING_ENTRY',
  'MONITORING',
  'TARGET_HIT',
  'CLOSING',
  'CLOSED',
  'EXIT_FAILED',
] as const
export type TargetStatus = (typeof TARGET_STATUSES)[number]

/** GET /api/trading/positions/targets - mirrors com.example.trading.dto.PositionTargetResponse. */
export interface PositionTarget {
  tradingSymbol: string
  underlying: string
  side: 'LONG' | 'SHORT'
  quantity: number
  /** ACTUAL Groww filled entry price; null until the fill is confirmed. */
  entryPrice: number | null
  /** User configuration snapshot (points). */
  targetPoints: number
  /** Backend-calculated: entryPrice + targetPoints; null until entryPrice is known. */
  targetPrice: number | null
  /** Last LTP the monitor observed for this exact contract. */
  currentLtp: number | null
  targetStatus: TargetStatus
  exitOrderReferenceId: string | null
  ltpAtTrigger: number | null
  updatedAt: string
}
