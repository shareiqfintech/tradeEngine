import { apiClient } from './apiClient'
import { cleanParams } from '@/lib/queryParams'
import type { AuditEvent, Page, RiskStatusResponse } from '@/types'

/**
 * NOT YET IMPLEMENTED on the backend. Documented expected contract:
 *   GET /api/trading/risk -> RiskStatusResponse
 *
 * Risk *rejection history* reuses the (also not-yet-implemented) audit
 * endpoint filtered to eventType=RISK_REJECTED, since AuditEventType.RISK_REJECTED
 * already exists server-side (RiskManagementService.evaluate() records it) -
 * this avoids inventing a second, redundant history endpoint.
 */
export const riskApi = {
  async getStatus(): Promise<RiskStatusResponse> {
    const { data } = await apiClient.get<RiskStatusResponse>('/api/trading/risk')
    return data
  },

  async getRejectionHistory(limit = 20): Promise<Page<AuditEvent>> {
    const { data } = await apiClient.get<Page<AuditEvent>>('/api/trading/audit', {
      params: cleanParams({ eventType: 'RISK_REJECTED', size: limit }),
    })
    return data
  },
}
