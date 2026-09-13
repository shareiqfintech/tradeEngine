import { useQuery } from '@tanstack/react-query'
import { positionTargetApi } from '@/api'
import { queryKeys } from './queryKeys'

const POSITION_TARGETS_POLL_MS = 5_000

/**
 * Automatic profit-target rows for the current user. Polls at the same
 * cadence as the raw positions view; the backend's own poller
 * (trading.exit.target.poll-interval) is what actually watches the LTP and
 * exits - this is display-only.
 */
export function usePositionTargets() {
  return useQuery({
    queryKey: queryKeys.positionTargets,
    queryFn: positionTargetApi.list,
    refetchInterval: POSITION_TARGETS_POLL_MS,
  })
}
