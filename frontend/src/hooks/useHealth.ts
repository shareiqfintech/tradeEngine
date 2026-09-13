import { useQuery } from '@tanstack/react-query'
import { healthApi } from '@/api'
import { queryKeys } from './queryKeys'

/** Health: poll every 10 seconds, per spec §15. */
const HEALTH_POLL_MS = 10_000

export function useActuatorHealth() {
  return useQuery({
    queryKey: queryKeys.healthActuator,
    queryFn: healthApi.getActuatorHealth,
    refetchInterval: HEALTH_POLL_MS,
  })
}

export function useHealthSummary() {
  return useQuery({
    queryKey: queryKeys.healthSummary,
    queryFn: healthApi.getSummary,
    refetchInterval: HEALTH_POLL_MS,
    retry: false,
  })
}
