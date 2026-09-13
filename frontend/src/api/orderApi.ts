import { apiClient } from './apiClient'
import { cleanParams } from '@/lib/queryParams'
import type { OrderFilters, Page, TradingOrder, TradingOrderDetail } from '@/types'

/**
 * NOT YET IMPLEMENTED on the backend. Documented expected contract:
 *
 *   GET /api/trading/orders?action=&status=&tradingSymbol=&mode=&from=&to=&page=&size=
 *     -> Page<TradingOrder>
 *   GET /api/trading/orders/{id}
 *     -> TradingOrderDetail
 *
 * `mode` (PAPER/LIVE) has no column on `orders` today either - it is
 * implied by the server's global `trading.mode` at the time the order was
 * placed. If this filter is added, the backend would need to either stamp
 * mode onto each order row or filter using its own config; the frontend
 * does not decide this.
 */
export const orderApi = {
  async list(filters: OrderFilters = {}): Promise<Page<TradingOrder>> {
    const { data } = await apiClient.get<Page<TradingOrder>>('/api/trading/orders', {
      params: cleanParams(filters),
    })
    return data
  },

  async getById(id: number | string): Promise<TradingOrderDetail> {
    const { data } = await apiClient.get<TradingOrderDetail>(`/api/trading/orders/${id}`)
    return data
  },
}
