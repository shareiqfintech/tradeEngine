import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { tradingApi } from '@/api'
import { ApiError } from '@/api/errors'
import { queryKeys } from './queryKeys'
import { toast } from './useToast'

/** Dashboard/status: poll every 5 seconds, per spec §15. */
const STATUS_POLL_MS = 5_000

export function useTradingStatus() {
  return useQuery({
    queryKey: queryKeys.tradingStatus,
    queryFn: tradingApi.getStatus,
    refetchInterval: STATUS_POLL_MS,
  })
}

function useControlMutation(action: () => Promise<{ status: string }>, successTitle: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: action,
    onSuccess: (result) => {
      toast.success(successTitle, `Backend acknowledged: ${result.status}`)
      void queryClient.invalidateQueries({ queryKey: queryKeys.tradingStatus })
    },
    onError: (error) => {
      const message = error instanceof ApiError ? error.message : 'Unknown error'
      toast.error('Action failed', message)
    },
  })
}

export function usePauseTrading() {
  return useControlMutation(tradingApi.pause, 'Trading paused')
}

export function useResumeTrading() {
  return useControlMutation(tradingApi.resume, 'Trading resumed')
}

export function useEnableKillSwitch() {
  return useControlMutation(tradingApi.enableKillSwitch, 'Kill switch enabled')
}

export function useDisableKillSwitch() {
  return useControlMutation(tradingApi.disableKillSwitch, 'Kill switch disabled')
}
