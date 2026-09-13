import type { TradingMode } from './domain'

/**
 * Response shape of GET /api/trading/config, mirroring
 * com.example.trading.config.TradingProperties - minus the `groww` block,
 * which the backend never returns (api-key and totp-secret are
 * credentials).
 */
export interface TradingConfigResponse {
  mode: TradingMode
  timezone: string
  allowShortSelling: boolean
  market: {
    start: string
    /** 15:10 IST safety-exit / new-entry cutoff (NOT the market close). */
    tradingCutoff: string
    /** 15:30 IST official NSE close. */
    close: string
  }
  quantity: {
    lots: number
  }
  option: {
    optionType: 'AUTO' | 'CE' | 'PE'
    strikeSelection: string
    strikeOffset: number
    expirySelection: 'NEAREST' | 'NEXT'
  }
  risk: {
    maxOrdersPerDay: number
    maxOpenPositions: number
    maxQuantity: number
    maxDailyLoss: number
    maxOrdersPerSignal: number
  }
  order: {
    product: string
    orderType: string
    validity: string
  }
}
