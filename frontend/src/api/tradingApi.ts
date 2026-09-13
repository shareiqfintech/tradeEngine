import { apiClient } from './apiClient'
import type { SimpleActionResponse, TradingStatusResponse } from '@/types'

/**
 * REAL, implemented endpoints - com.example.trading.controller.TradingAdminController.
 * Base path: /api/trading
 */
export const tradingApi = {
  async getStatus(): Promise<TradingStatusResponse> {
    const { data } = await apiClient.get<TradingStatusResponse>('/api/trading/status')
    return data
  },

  async pause(): Promise<SimpleActionResponse> {
    const { data } = await apiClient.post<SimpleActionResponse>('/api/trading/pause')
    return data
  },

  async resume(): Promise<SimpleActionResponse> {
    const { data } = await apiClient.post<SimpleActionResponse>('/api/trading/resume')
    return data
  },

  async enableKillSwitch(): Promise<SimpleActionResponse> {
    const { data } = await apiClient.post<SimpleActionResponse>('/api/trading/kill-switch/enable')
    return data
  },

  async disableKillSwitch(): Promise<SimpleActionResponse> {
    const { data } = await apiClient.post<SimpleActionResponse>('/api/trading/kill-switch/disable')
    return data
  },
}
