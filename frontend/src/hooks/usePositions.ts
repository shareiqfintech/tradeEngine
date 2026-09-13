import { useQuery } from '@tanstack/react-query'
import { positionApi } from '@/api'
import { queryKeys } from './queryKeys'

const POSITIONS_POLL_MS = 5_000

export function usePositions() {
  return useQuery({
    queryKey: queryKeys.positions,
    queryFn: positionApi.list,
    refetchInterval: POSITIONS_POLL_MS,
  })
}
