import { apiClient } from './apiClient'
import { cleanParams } from '@/lib/queryParams'
import type { AuditEvent, AuditFilters, Page } from '@/types'

/**
 * NOT YET IMPLEMENTED on the backend. Documented expected contract:
 *   GET /api/trading/audit?eventType=&signalId=&orderReferenceId=&from=&to=&page=&size=
 *     -> Page<AuditEvent>
 *
 * The data source (AuditEventEntity/AuditEventRepository) is real and
 * already populated by AuditService on every significant event - only the
 * HTTP read endpoint is missing.
 */
export const auditApi = {
  async list(filters: AuditFilters = {}): Promise<Page<AuditEvent>> {
    const { data } = await apiClient.get<Page<AuditEvent>>('/api/trading/audit', {
      params: cleanParams(filters),
    })
    return data
  },
}
