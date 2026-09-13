import type { TradingStatusResponse } from '@/types'

export type TradingStatusLabel =
  | 'ENABLED'
  | 'PAUSED'
  | 'KILL_SWITCH'
  | 'AUTH_REQUIRED'
  | 'SESSION_CLOSING'
  | 'MARKET_CLOSED'

/**
 * Derives one headline status label from the real fields on
 * TradingStatusResponse. Priority mirrors the server-side overlay order
 * (killSwitch > paused > auth > session lifecycle) - purely a presentation
 * decision, not a new business rule.
 */
export function deriveTradingStatusLabel(status: TradingStatusResponse): TradingStatusLabel {
  if (status.killSwitch) return 'KILL_SWITCH'
  if (status.paused) return 'PAUSED'
  if (!status.growwAuthenticated) return 'AUTH_REQUIRED'
  if (status.sessionState === 'SESSION_CLOSING') return 'SESSION_CLOSING'
  if (!status.tradingEnabled) return 'MARKET_CLOSED'
  return 'ENABLED'
}
