import { apiClient } from './apiClient'
import type { FnoTradeConfigRequest, FnoTradeConfigResponse } from '@/types'

/**
 * REAL, implemented endpoints - com.example.trading.controller.FnoTradeConfigController.
 * Base path: /api/trading/fno
 *
 * The list of underlyings is sourced live from Groww's instrument master
 * (InstrumentMasterService) - never a hardcoded ['NIFTY','BANKNIFTY',...]
 * array here or on the backend. Lot size validation/selection happens
 * entirely server-side (OptionContractResolver) - this module only ever
 * relays what the backend decided.
 */
export const fnoConfigApi = {
  async listUnderlyings(): Promise<string[]> {
    const { data } = await apiClient.get<string[]>('/api/trading/fno/underlyings')
    return data
  },

  async getConfig(underlying: string): Promise<FnoTradeConfigResponse> {
    const { data } = await apiClient.get<FnoTradeConfigResponse>(
      `/api/trading/fno/config/${encodeURIComponent(underlying)}`,
    )
    return data
  },

  async updateConfig(underlying: string, request: FnoTradeConfigRequest): Promise<FnoTradeConfigResponse> {
    const { data } = await apiClient.put<FnoTradeConfigResponse>(
      `/api/trading/fno/config/${encodeURIComponent(underlying)}`,
      request,
    )
    return data
  },
}
