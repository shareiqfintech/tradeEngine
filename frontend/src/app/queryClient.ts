import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/api/errors'

/**
 * Shared defaults: don't hammer a down/misconfigured backend with retries,
 * and don't poll while the tab is in the background (TanStack Query's
 * `refetchIntervalInBackground` defaults to false, which we rely on for
 * every polling hook in src/hooks/).
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status !== null && error.status < 500) {
          return false // 4xx won't succeed on retry - fail fast
        }
        return failureCount < 2
      },
      refetchOnWindowFocus: true,
      staleTime: 2_000,
    },
    mutations: {
      retry: false,
    },
  },
})
