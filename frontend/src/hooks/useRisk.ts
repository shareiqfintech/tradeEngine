import { useQuery } from '@tanstack/react-query'
import { riskApi } from '@/api'
import { queryKeys } from './queryKeys'

const RISK_POLL_MS = 5_000

export function useRiskStatus() {
  return useQuery({
    queryKey: queryKeys.risk,
    queryFn: riskApi.getStatus,
    refetchInterval: RISK_POLL_MS,
  })
}

export function useRiskRejectionHistory() {
  return useQuery({
    queryKey: queryKeys.riskRejections,
    queryFn: () => riskApi.getRejectionHistory(20),
    refetchInterval: RISK_POLL_MS,
  })
}
