import { apiClient } from './apiClient'
import type { TradingPosition } from '@/types'

/**
 * NOT YET IMPLEMENTED on the backend as an HTTP endpoint. The data itself
 * is real server-side (GrowwPositionService.getPositions() queries Groww's
 * live positions API), but nothing currently exposes it over HTTP -
 * TradingAdminController only uses it internally to compute the
 * `openPositions` count in GET /api/trading/status.
 *
 * Documented expected contract:
 *   GET /api/trading/positions -> TradingPosition[]
 */
export const positionApi = {
  async list(): Promise<TradingPosition[]> {
    const { data } = await apiClient.get<TradingPosition[]>('/api/trading/positions')
    return data
  },
}
