import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { growwSettingsApi, type GrowwSettingsRequest } from '@/api/growwSettingsApi'
import { ApiError } from '@/api/errors'
import { queryKeys } from './queryKeys'
import { toast } from './useToast'

export function useGrowwSettings() {
  return useQuery({
    queryKey: queryKeys.growwSettings,
    queryFn: growwSettingsApi.getSettings,
  })
}

export function useSaveGrowwSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: GrowwSettingsRequest) => growwSettingsApi.saveSettings(request),
    onSuccess: (response) => {
      queryClient.setQueryData(queryKeys.growwSettings, response)
      toast.success('Groww configuration saved')
    },
    onError: (error) => {
      const message = error instanceof ApiError ? error.message : 'Unknown error'
      toast.error('Could not save Groww configuration', message)
    },
  })
}

export function useTestGrowwConnection() {
  return useMutation({
    mutationFn: growwSettingsApi.testConnection,
    onError: (error) => {
      const message = error instanceof ApiError ? error.message : 'Unknown error'
      toast.error('Could not test Groww connection', message)
    },
  })
}
