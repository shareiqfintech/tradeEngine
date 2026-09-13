import { apiClient } from './apiClient'
import { cleanParams } from '@/lib/queryParams'
import type { Page, SignalFilters, TradingSignal, TradingSignalDetail } from '@/types'

/**
 * NOT YET IMPLEMENTED on the backend. Documented expected contract:
 *
 *   GET /api/trading/signals?action=&status=&underlying=&search=&from=&to=&page=&size=
 *     -> Page<TradingSignal>
 *   GET /api/trading/signals/{signalId}
 *     -> TradingSignalDetail
 *
 * Until these exist, calls below will fail (404/501) and the Signals page
 * surfaces that honestly via ErrorState - it never falls back to mock data.
 */
export const signalApi = {
  async list(filters: SignalFilters = {}): Promise<Page<TradingSignal>> {
    const { data } = await apiClient.get<Page<TradingSignal>>('/api/trading/signals', {
      params: cleanParams(filters),
    })
    return data
  },

  async getBySignalId(signalId: string): Promise<TradingSignalDetail> {
    const { data } = await apiClient.get<TradingSignalDetail>(
      `/api/trading/signals/${encodeURIComponent(signalId)}`,
    )
    return data
  },
}
