import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthProvider'
import { PageLoadingFallback } from '@/components/shared/PageLoadingFallback'

/** Redirects to /signin when there is no authenticated session; used to gate the whole authenticated app shell (dashboard, settings, etc). */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return <PageLoadingFallback />
  }
  if (!isAuthenticated) {
    return <Navigate to="/signin" state={{ from: location }} replace />
  }
  return <>{children}</>
}
