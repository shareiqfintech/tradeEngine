import { lazy, Suspense, type ReactNode } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { PageLoadingFallback } from '@/components/shared/PageLoadingFallback'
import { RequireAuth } from './RequireAuth'

const SignInPage = lazy(() => import('@/pages/SignInPage').then((m) => ({ default: m.SignInPage })))
const SignUpPage = lazy(() => import('@/pages/SignUpPage').then((m) => ({ default: m.SignUpPage })))
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })))
const DashboardPage = lazy(() => import('@/pages/DashboardPage').then((m) => ({ default: m.DashboardPage })))
const SignalsPage = lazy(() => import('@/pages/SignalsPage').then((m) => ({ default: m.SignalsPage })))
const OrdersPage = lazy(() => import('@/pages/OrdersPage').then((m) => ({ default: m.OrdersPage })))
const PositionsPage = lazy(() => import('@/pages/PositionsPage').then((m) => ({ default: m.PositionsPage })))
const RiskPage = lazy(() => import('@/pages/RiskPage').then((m) => ({ default: m.RiskPage })))
const ActivityPage = lazy(() => import('@/pages/ActivityPage').then((m) => ({ default: m.ActivityPage })))
const HealthPage = lazy(() => import('@/pages/HealthPage').then((m) => ({ default: m.HealthPage })))
const SettingsPage = lazy(() => import('@/pages/SettingsPage').then((m) => ({ default: m.SettingsPage })))

function withSuspense(element: ReactNode) {
  return <Suspense fallback={<PageLoadingFallback />}>{element}</Suspense>
}

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/dashboard" replace /> },
  { path: '/login', element: <Navigate to="/signin" replace /> },
  { path: '/signin', element: withSuspense(<SignInPage />) },
  { path: '/signup', element: withSuspense(<SignUpPage />) },
  {
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { path: '/dashboard', element: withSuspense(<DashboardPage />) },
      { path: '/signals', element: withSuspense(<SignalsPage />) },
      { path: '/orders', element: withSuspense(<OrdersPage />) },
      { path: '/positions', element: withSuspense(<PositionsPage />) },
      { path: '/risk', element: withSuspense(<RiskPage />) },
      { path: '/activity', element: withSuspense(<ActivityPage />) },
      { path: '/health', element: withSuspense(<HealthPage />) },
      { path: '/settings', element: withSuspense(<SettingsPage />) },
    ],
  },
  { path: '*', element: withSuspense(<NotFoundPage />) },
])
