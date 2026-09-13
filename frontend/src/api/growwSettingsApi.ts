import { apiClient } from './apiClient'

/**
 * GET  /api/settings/groww               -> GrowwSettingsResponse
 * PUT  /api/settings/groww                { apiKey?, totpSecret? } -> GrowwSettingsResponse
 * POST /api/settings/groww/test-connection -> GrowwConnectionTestResponse
 *
 * Never carries the full API key or TOTP secret back from the backend -
 * see GrowwSettingsResponse. When saving, omit a field entirely (leave it
 * `undefined`) to mean "keep the currently-saved value" - never send back
 * the masked placeholder the UI displays.
 */
export interface GrowwSettingsResponse {
  configured: boolean
  apiKeyConfigured: boolean
  totpConfigured: boolean
  /** True only after a real, successful Test Connection call - changing the API key/TOTP secret flips this back to false until re-verified. */
  connected: boolean
  maskedApiKey: string | null
}

export interface GrowwSettingsRequest {
  apiKey?: string
  totpSecret?: string
}

export interface GrowwConnectionTestResponse {
  connected: boolean
  message: string
}

export const growwSettingsApi = {
  async getSettings(): Promise<GrowwSettingsResponse> {
    const { data } = await apiClient.get<GrowwSettingsResponse>('/api/settings/groww')
    return data
  },

  async saveSettings(request: GrowwSettingsRequest): Promise<GrowwSettingsResponse> {
    const { data } = await apiClient.put<GrowwSettingsResponse>('/api/settings/groww', request)
    return data
  },

  async testConnection(): Promise<GrowwConnectionTestResponse> {
    const { data } = await apiClient.post<GrowwConnectionTestResponse>('/api/settings/groww/test-connection')
    return data
  },
}
