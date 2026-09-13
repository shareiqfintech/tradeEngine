import type { RiskLevel } from '@/types'

/** Client-side-only classification of usage ratio into a display risk level. Never used to block anything - the backend's RiskManagementService is the sole authority on approving/rejecting orders. */
export function riskLevelFor(used: number, limit: number): RiskLevel {
  if (limit <= 0) return 'SAFE'
  const ratio = used / limit
  if (ratio >= 1) return 'BLOCKED'
  if (ratio >= 0.85) return 'CRITICAL'
  if (ratio >= 0.6) return 'WARNING'
  return 'SAFE'
}

export const RISK_LEVEL_COLOR: Record<RiskLevel, string> = {
  SAFE: 'bg-success',
  WARNING: 'bg-warning',
  CRITICAL: 'bg-destructive',
  BLOCKED: 'bg-destructive',
}

export const RISK_LEVEL_BADGE_VARIANT: Record<RiskLevel, 'success' | 'warning' | 'destructive'> = {
  SAFE: 'success',
  WARNING: 'warning',
  CRITICAL: 'destructive',
  BLOCKED: 'destructive',
}
