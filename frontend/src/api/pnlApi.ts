import { apiClient } from './apiClient'
import type { PnlPoint, PnlSummaryResponse } from '@/types'

/**
 * NOT YET IMPLEMENTED on the backend - see PnlSummaryResponse/PnlPoint for
 * exactly what is and isn't real server-side today. Documented expected
 * contract:
 *   GET /api/trading/pnl/summary   -> PnlSummaryResponse
 *   GET /api/trading/pnl/intraday  -> PnlPoint[]
 */
export const pnlApi = {
  async getSummary(): Promise<PnlSummaryResponse> {
    const { data } = await apiClient.get<PnlSummaryResponse>('/api/trading/pnl/summary')
    return data
  },

  async getIntraday(): Promise<PnlPoint[]> {
    const { data } = await apiClient.get<PnlPoint[]>('/api/trading/pnl/intraday')
    return data
  },
}
