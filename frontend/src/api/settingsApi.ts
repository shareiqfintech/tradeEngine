import { apiClient } from './apiClient'
import type { TradingConfigResponse } from '@/types'

/**
 * GET /api/trading/config -> TradingConfigResponse, backed by
 * com.example.trading.config.TradingProperties (TradingQueryController).
 * Read-only: there is no write endpoint, since safe runtime
 * reconfiguration of risk/trading parameters is a backend decision, not a
 * frontend one.
 */
export const settingsApi = {
  async getConfig(): Promise<TradingConfigResponse> {
    const { data } = await apiClient.get<TradingConfigResponse>('/api/trading/config')
    return data
  },
}
