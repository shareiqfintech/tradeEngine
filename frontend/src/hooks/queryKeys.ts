import type { AuditFilters, OrderFilters, SignalFilters } from '@/types'

/** Centralized TanStack Query key factory - keeps cache invalidation consistent across hooks. */
export const queryKeys = {
  tradingStatus: ['trading', 'status'] as const,
  signals: (filters: SignalFilters) => ['signals', 'list', filters] as const,
  signal: (signalId: string) => ['signals', 'detail', signalId] as const,
  orders: (filters: OrderFilters) => ['orders', 'list', filters] as const,
  order: (id: number | string) => ['orders', 'detail', id] as const,
  positions: ['positions', 'list'] as const,
  positionTargets: ['positions', 'targets'] as const,
  pnlSummary: ['pnl', 'summary'] as const,
  pnlIntraday: ['pnl', 'intraday'] as const,
  risk: ['risk', 'status'] as const,
  riskRejections: ['risk', 'rejections'] as const,
  audit: (filters: AuditFilters) => ['audit', 'list', filters] as const,
  healthActuator: ['health', 'actuator'] as const,
  healthSummary: ['health', 'summary'] as const,
  settingsConfig: ['settings', 'config'] as const,
  growwSettings: ['settings', 'groww'] as const,
  fnoUnderlyings: ['fno', 'underlyings'] as const,
  fnoConfig: (underlying: string) => ['fno', 'config', underlying] as const,
}
