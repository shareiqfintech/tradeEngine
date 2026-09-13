import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fnoConfigApi } from '@/api'
import { ApiError } from '@/api/errors'
import { queryKeys } from './queryKeys'
import { toast } from './useToast'
import type { FnoTradeConfigRequest } from '@/types'

/** Available underlyings, sourced live from Groww's instrument master - never a hardcoded list. Rarely changes, so no aggressive polling. */
export function useFnoUnderlyings() {
  return useQuery({
    queryKey: queryKeys.fnoUnderlyings,
    queryFn: fnoConfigApi.listUnderlyings,
    staleTime: 60_000,
  })
}

export function useFnoTradeConfig(underlying: string | undefined) {
  return useQuery({
    queryKey: queryKeys.fnoConfig(underlying ?? ''),
    queryFn: () => fnoConfigApi.getConfig(underlying as string),
    enabled: Boolean(underlying),
    retry: false,
  })
}

/**
 * Saves the per-underlying F&O configuration. The backend re-resolves the
 * exact contract and (if a lot size was supplied) re-validates it against
 * Groww's real instrument master on every call - this mutation's response
 * is the only source of truth for the resolved contract/effective lot
 * size/quantity; nothing is computed client-side as authoritative.
 */
export function useUpdateFnoTradeConfig(underlying: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: FnoTradeConfigRequest) => fnoConfigApi.updateConfig(underlying as string, request),
    onSuccess: (response) => {
      queryClient.setQueryData(queryKeys.fnoConfig(response.underlying), response);
    },
    onError: (error) => {
      const message = error instanceof ApiError ? error.message : 'Unknown error'
      toast.error('Could not save F&O configuration', message)
    },
  })
}
