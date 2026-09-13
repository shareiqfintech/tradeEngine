import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { signalApi } from '@/api'
import { queryKeys } from './queryKeys'
import type { SignalFilters } from '@/types'

const SIGNALS_POLL_MS = 5_000

export function useSignals(filters: SignalFilters) {
  return useQuery({
    queryKey: queryKeys.signals(filters),
    queryFn: () => signalApi.list(filters),
    refetchInterval: SIGNALS_POLL_MS,
    placeholderData: keepPreviousData,
  })
}

export function useSignal(signalId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.signal(signalId ?? ''),
    queryFn: () => signalApi.getBySignalId(signalId as string),
    enabled: Boolean(signalId),
  })
}
