import { useQuery } from '@tanstack/react-query'
import { settingsApi } from '@/api'
import { queryKeys } from './queryKeys'

/** Config is effectively static at runtime (only changes on backend restart) - no polling needed. */
export function useTradingConfig() {
  return useQuery({
    queryKey: queryKeys.settingsConfig,
    queryFn: settingsApi.getConfig,
    staleTime: 60_000,
    refetchInterval: false,
  })
}
