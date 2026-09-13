import { apiClient } from './apiClient'
import type { PositionTarget } from '@/types'

/**
 * REAL, implemented - com.example.trading.controller.TradingQueryController.
 *   GET /api/trading/positions/targets -> PositionTarget[]  (calling user's rows)
 *
 * Automatic profit-target monitoring: Target Points (config), the ACTUAL
 * Groww entry price, the backend-calculated Target Price, the last observed
 * LTP, and the monitor status. Never carries any Groww credential.
 */
export const positionTargetApi = {
  async list(): Promise<PositionTarget[]> {
    const { data } = await apiClient.get<PositionTarget[]>('/api/trading/positions/targets')
    return data
  },
}
