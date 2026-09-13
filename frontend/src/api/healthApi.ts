import { apiClient } from './apiClient'
import type { ActuatorHealthResponse, HealthSummaryResponse } from '@/types'

/**
 * `getActuatorHealth` calls a REAL, already-exposed endpoint - Spring Boot
 * Actuator's `/actuator/health`, enabled via
 * `management.endpoints.web.exposure.include: health,info` in the
 * backend's application.yml. IMPORTANT caveat documented in
 * frontend/README.md: without `management.endpoint.health.show-details:
 * always` (or `when-authorized`) set on the backend, the response today is
 * just `{"status":"UP"}` with no `components` breakdown - so the frontend
 * cannot show real per-component (DB/Redis) rows until that one backend
 * config line is added. The UI reflects this honestly instead of inventing
 * a breakdown.
 *
 * `getSummary` calls a NOT-YET-IMPLEMENTED endpoint - see HealthSummaryResponse.
 */
export const healthApi = {
  async getActuatorHealth(): Promise<ActuatorHealthResponse> {
    const { data } = await apiClient.get<ActuatorHealthResponse>('/actuator/health')
    return data
  },

  async getSummary(): Promise<HealthSummaryResponse> {
    const { data } = await apiClient.get<HealthSummaryResponse>('/api/health/summary')
    return data
  },
}
