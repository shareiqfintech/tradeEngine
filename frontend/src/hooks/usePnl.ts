import { useQuery } from '@tanstack/react-query'
import { pnlApi } from '@/api'
import { queryKeys } from './queryKeys'

const PNL_POLL_MS = 5_000

export function usePnlSummary() {
  return useQuery({
    queryKey: queryKeys.pnlSummary,
    queryFn: pnlApi.getSummary,
    refetchInterval: PNL_POLL_MS,
    retry: false,
  })
}

export function usePnlIntraday() {
  return useQuery({
    queryKey: queryKeys.pnlIntraday,
    queryFn: pnlApi.getIntraday,
    refetchInterval: PNL_POLL_MS,
    retry: false,
  })
}
